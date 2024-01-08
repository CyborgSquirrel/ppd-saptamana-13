package server;

import protocol.MsgScoreEntry;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class Main {
  static final int MAX_QUEUE_SIZE = 50;

  class ScoreEntry {

  }

  class MessageQueue {
    Queue<ScoreEntry> queue;
    ReentrantLock queueLock;

    Condition canSendCond;
    Condition canRecvCond;

    boolean closed;

    MessageQueue() {
      this.queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
      this.queueLock = new ReentrantLock(true);
      this.canSendCond = queueLock.newCondition();
      this.canRecvCond = queueLock.newCondition();
      this.closed = false;
    }

    void close() {
      this.queueLock.lock();

      while (this.queue.size() >= MAX_QUEUE_SIZE) {
        try {
          this.canSendCond.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      this.queueLock.unlock();
      this.canRecvCond.notify();
    }

    Optional<ScoreEntry> recv() {
      this.queueLock.lock();

      while (this.queue.size() <= 0 && !this.closed) {
        try {
          this.canRecvCond.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      if (this.queue.size() <= 0) {
        this.queueLock.unlock();
        return Optional.empty();
      }

      var result = Optional.of(this.queue.poll());
      this.canSendCond.notify();
      return result;
    }
  }

  class ScoreTotal {
    int id;
    int score;
    int country;
  }

  // linked list

  class LListItem {
    ScoreTotal scoreTotal;
    ReentrantLock lock;

    LListItem(ScoreTotal scoreTotal) {
      this.scoreTotal = scoreTotal;
    }
  }

  class LListNode {
    LListItem item;
    LListNode node;
  }

  class LList {
    String path;
    LListNode head;
  }

  public static void main(String[] args) throws IOException {
    int p_r = Integer.parseInt(args[0]);
    int p_w = Integer.parseInt(args[1]);

    ExecutorService executorService = Executors.newFixedThreadPool(p_r);

    ServerSocket serverSocket = new ServerSocket(42069);
    while (true) {
      Socket clientSocket = serverSocket.accept();
      executorService.submit(() -> {
        ObjectInputStream inputStream = null;

        try {
          inputStream = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        try {
          var outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        while (!clientSocket.isClosed()) {
          Object object = null;
          try {
            object = inputStream.readObject();
          } catch (IOException e) {
            throw new RuntimeException(e);
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }

          if (object instanceof MsgScoreEntry) {

          }
        }
      });
    }
  }
}
