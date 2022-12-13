import enum
import logging
import llp
import queue
import struct
import threading

class SWPType(enum.IntEnum):
    DATA = ord('D')
    ACK = ord('A')

class SWPPacket:
    _PACK_FORMAT = '!BI'
    _HEADER_SIZE = struct.calcsize(_PACK_FORMAT)
    MAX_DATA_SIZE = 1400 # Leaves plenty of space for IP + UDP + SWP header 

    def __init__(self, type, seq_num, data=b''):
        self._type = type
        self._seq_num = seq_num
        self._data = data

    @property
    def type(self):
        return self._type

    @property
    def seq_num(self):
        return self._seq_num
    
    @property
    def data(self):
        return self._data

    def to_bytes(self):
        header = struct.pack(SWPPacket._PACK_FORMAT, self._type.value, 
                self._seq_num)
        return header + self._data
       
    @classmethod
    def from_bytes(cls, raw):
        header = struct.unpack(SWPPacket._PACK_FORMAT,
                raw[:SWPPacket._HEADER_SIZE])
        type = SWPType(header[0])
        seq_num = header[1]
        data = raw[SWPPacket._HEADER_SIZE:]
        return SWPPacket(type, seq_num, data)

    def __str__(self):
        return "%s %d %s" % (self._type.name, self._seq_num, repr(self._data))

class SWPSender:
    _SEND_WINDOW_SIZE = 5
    _TIMEOUT = 1

    def __init__(self, remote_address, loss_probability=0):
        self._llp_endpoint = llp.LLPEndpoint(remote_address=remote_address,
                loss_probability=loss_probability)

        # Start receive thread
        self._recv_thread = threading.Thread(target=self._recv)
        self._recv_thread.start()
        
        # TODO: Add additional state variables
        # the sequence num
        self.packetSeq = 0
        # last unacked seq
        self.last_unacked = 0
        
        self.sendBuffer = {}
        self.timers = {}
        self.win_sem = threading.Semaphore(self._SEND_WINDOW_SIZE)   # used to limited the window size
        self.buf_sem = threading.Semaphore()   # using to handle buf concureent issues
        self.timer_sem = threading.Semaphore() # using to handle timer concureent issues


    def send(self, data):
        for i in range(0, len(data), SWPPacket.MAX_DATA_SIZE):
            self._send(data[i:i+SWPPacket.MAX_DATA_SIZE])

    def _send(self, data):
        # TODO
        # Wait for a free space in the send window
        self.win_sem.acquire()
        
        
        # Assign the chunk of data a sequence number
        seqNum = self.packetSeq
        
        self.buf_sem.acquire()
        # Add the chunk of data to a buffer
        data = data.decode("utf-8")
        data = data.replace("\n","")
        data = data.encode("utf-8")
        logging.debug("type data: %s" % type(data))
        self.sendBuffer[seqNum] = data
        
        self.buf_sem.release()
        # send the data in an SWP packet
        swpPacket = SWPPacket(type=SWPType.DATA, seq_num=seqNum, data=data)
        self._llp_endpoint.send(swpPacket.to_bytes())
        # Start a retransmission timer
        retrans_timer = threading.Timer(interval=self._TIMEOUT, function=self._retransmit, args=[seqNum])
        retrans_timer.start()
        
        self.timer_sem.acquire()
        # add this timer to the list
        self.timers[seqNum] = retrans_timer
        
        self.timer_sem.release()
        # increment Sequence number
        self.packetSeq = self.packetSeq + 1
        return
        
    def _retransmit(self, seq_num):
        # TODO
        if (seq_num < self.last_unacked):
            return
        logging.debug("send packet times: %d" % seq_num)
        self.buf_sem.acquire()
        if seq_num not in self.sendBuffer.keys():
            return
        data = self.sendBuffer.get(seq_num)
        self.buf_sem.release()
        # send the data in an SWP packet
        swpPacket = SWPPacket(type=SWPType.DATA, seq_num=seq_num, data=data)
        self._llp_endpoint.send(swpPacket.to_bytes())
        # Start a retransmission timer
        retrans_timer = threading.Timer(interval=self._TIMEOUT, function=self._retransmit, args=[seq_num])
        retrans_timer.start()
        
        self.timer_sem.acquire()
        # add this timer to the list
        # check whether still a timer
        if seq_num not in self.timers.keys():
            return
        used_timer = self.timers.pop(seq_num)
        used_timer.cancel()
        self.timers[seq_num] = retrans_timer
        self.timer_sem.release()
        return 

    def _recv(self):
        while True:
            # Receive SWP packet
            raw = self._llp_endpoint.recv()
            if raw is None:
                continue
            packet = SWPPacket.from_bytes(raw)
            logging.debug("Received: %s" % packet)

            # TODO
            # get the ack, if not continue
            if (packet.type != SWPType.ACK):
                logging.debug("Received not ack: %s" % packet.type)
                continue
            # get the sequence number
            seq_num = packet.seq_num
            # mark all unacked seq num before the recieved seq_num
            for unacked in range(self.last_unacked, seq_num+1):
                # Cancel the retransmission timer for that chunk of data.
                self.timer_sem.acquire()
                if unacked in self.timers.keys():
                    timer = self.timers.pop(unacked)
                    logging.debug("Cancel the timer: %d" % unacked)
                    timer.cancel()
                self.timer_sem.release()
                self.buf_sem.acquire()
                self.sendBuffer.pop(seq_num)
                self.buf_sem.release()
                # Signal that there is now a free space in the send window.
                self.win_sem.release() 
            self.last_unacked = seq_num+1
            logging.debug("last_unacked: %d" % self.last_unacked)
        return

class SWPReceiver:
    _RECV_WINDOW_SIZE = 5

    def __init__(self, local_address, loss_probability=0):
        self._llp_endpoint = llp.LLPEndpoint(local_address=local_address, 
                loss_probability=loss_probability)

        # Received data waiting for application to consume
        self._ready_data = queue.Queue()

        # Start receive thread
        self._recv_thread = threading.Thread(target=self._recv)
        self._recv_thread.start()
        
        # TODO: Add additional state variables
        self.recv_buf = {}
        self.high_seq = -1
        self.buf_sem = threading.Semaphore()


    def recv(self):
        return self._ready_data.get()

    def _recv(self):
        while True:
            # Receive data packet
            raw = self._llp_endpoint.recv()
            packet = SWPPacket.from_bytes(raw)
            logging.debug("Received: %s" % packet)
            # TODO
            # check whether recieve correct type
            pkt_type = packet.type
            if (pkt_type != SWPType.DATA):
                continue
            # get the seq_num
            seq_num = packet.seq_num
            
            # if already acknowledged, retransmit an SWP ACK
            if seq_num <= self.high_seq:
                swpPacket = SWPPacket(type=SWPType.ACK, seq_num=self.high_seq)
                self._llp_endpoint.send(swpPacket.to_bytes())
                continue
            # Add the chunk of data to a buffer—in case it is out of order.
            self.buf_sem.acquire()
            self.recv_buf[seq_num] = packet.data
            # Traverse the buffer, starting from the first buffered chunk of data
            unacked = self.high_seq+1
            while True:
                # reaching  “hole”
                if unacked not in self.recv_buf.keys():
                    self.high_seq = unacked-1
                    break
                else:
                    data = self.recv_buf.get(unacked)
                    self._ready_data.put(data)
                    self.recv_buf.pop(unacked)
                    unacked = unacked + 1
            self.buf_sem.release()
            swpPacket = SWPPacket(type=SWPType.ACK, seq_num=self.high_seq)
            self._llp_endpoint.send(swpPacket.to_bytes())             
        return
