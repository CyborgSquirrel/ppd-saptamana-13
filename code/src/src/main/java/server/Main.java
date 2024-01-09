package server;

import protocol.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Main {
  static final int MAX_QUEUE_SIZE = 50;

  static class ScoreEntry {
    int id;
    int score;
    int countryId;

    ScoreEntry(int id, int score, int countryId) {
      this.id = id;
      this.score = score;
      this.countryId = countryId;
    }
  }

  static class MessageQueue {
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
      this.closed = true;
      this.canRecvCond.notifyAll();
      this.queueLock.unlock();
    }

    void send(ScoreEntry value) {
      this.queueLock.lock();

      while (this.queue.size() >= MAX_QUEUE_SIZE) {
        try {
          this.canSendCond.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      this.queue.add(value);

      this.canRecvCond.notify();
      this.queueLock.unlock();
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

  static class ScoreTotal {
    int id;
    int score;
    int countryId;

    public ScoreTotal(int id, int score, int countryId) {
      this.id = id;
      this.score = score;
      this.countryId = countryId;
    }
  }

  // linked list

  static class LListItem {
    ScoreTotal scoreTotal;
    ReentrantLock lock;

    LListItem(ScoreTotal scoreTotal) {
      this.scoreTotal = scoreTotal;
      this.lock = new ReentrantLock();
    }
  }

  static class LListNode {
    LListItem item;
    LListNode next;

    public LListNode(LListItem item, LListNode next) {
      this.item = item;
      this.next = next;
    }
  }

  static class LList {
    LListNode head;

    public LList() {
      this.head = null;
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    int p_r = Integer.parseInt(args[0]);
    int p_w = Integer.parseInt(args[1]);
    float delta_t = Float.parseFloat(args[2]);

    // setup
    MessageQueue messageQueue = new MessageQueue();
    Set<Integer> cheaters = new HashSet<>();
    ReentrantLock cheatersLock = new ReentrantLock();
    LList llist = new LList();
    llist.head = new LListNode(new LListItem(null), null);

    CountDownLatch finishedWorking = new CountDownLatch(p_w);

    Queue<Future<Void>> mainFuturesQueue = new ConcurrentLinkedQueue<>();

    // workers
    Thread[] workerThreads = new Thread[p_w];
    for (int i = 0; i < p_w; ++i) {
      workerThreads[i] = new Thread(() -> {
        while (true) {
          Optional<ScoreEntry> entryPerhaps = messageQueue.recv();
          if (entryPerhaps.isEmpty()) {
            break;
          }

          ScoreEntry entry = entryPerhaps.get();
          boolean isCheater = entry.score == -1;

          cheatersLock.lock();
          if (cheaters.contains(entry.id)) {
            cheatersLock.unlock();
            continue;
          } else if (isCheater) {
            cheaters.add(entry.id);
          }

          {
            LListNode nodePrev = llist.head     ; nodePrev.item.lock.lock();
            LListNode node     = llist.head.next;

            cheatersLock.unlock();

            boolean found = false;
            while (node != null && !found) {
              node.item.lock.lock();

              if (node.item.scoreTotal.id == entry.id) {
                found = true;
                if (isCheater) {
                  nodePrev.next = node.next;
                } else {
                  node.item.scoreTotal.score += entry.score;
                }
              }

              nodePrev.item.lock.unlock();
              nodePrev = node;
              node = node.next;
            }

            if (!found && !isCheater) {
              LListNode newNode = new LListNode(new LListItem(new ScoreTotal(entry.id, entry.score, entry.countryId)), null);
              nodePrev.next = newNode;
            }
            nodePrev.item.lock.unlock();
          }
        }
        finishedWorking.countDown();
      });
      workerThreads[i].start();
    }

    // socket server
    Thread serverThread = new Thread(() -> {
      Phaser socketPhaser = new Phaser();

      ExecutorService executorService = Executors.newFixedThreadPool(p_r);
      ServerSocket serverSocket = null;
      try {
        serverSocket = new ServerSocket(42069);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      int countryIdIter = 0;
      while (true) {
        Socket clientSocket = null;
        try {
          clientSocket = serverSocket.accept();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        // java complains without this *awesome* boilerplate
        int finalCountryIdIter = countryIdIter;

        Socket finalClientSocket = clientSocket;
        executorService.submit(() -> {
          int countryId = finalCountryIdIter;

          ObjectInputStream inputStream = null;
          try {
            inputStream = new ObjectInputStream(finalClientSocket.getInputStream());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          ObjectOutputStream outputStream = null;
          try {
            outputStream = new ObjectOutputStream(finalClientSocket.getOutputStream());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          socketPhaser.register();
          while (!finalClientSocket.isClosed()) {
            Object object = null;
            try {
              object = inputStream.readObject();
            } catch (IOException e) {
              throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
              throw new RuntimeException(e);
            }

            if (object instanceof MsgScoreEntries objectSpec) {
              for (MsgScoreEntry msgScoreEntry : objectSpec.msgScoreEntries) {
                ScoreEntry scoreEntry = new ScoreEntry(
                        msgScoreEntry.id,
                        msgScoreEntry.score,
                        countryId);
                messageQueue.send(scoreEntry);
              }
            } else if (object instanceof MsgGetStatus) {
              // TODO: delta_t garbage
              ObjectOutputStream finalOutputStream = outputStream;
              mainFuturesQueue.add(new FutureTask<>(() -> {
                LListNode nodePrev = llist.head     ; nodePrev.item.lock.lock();
                LListNode node     = llist.head.next;

                Map<Integer, Integer> scoreTotal = new HashMap<>();

                while (node != null) {
                  node.item.lock.lock();

                  LListNode finalNode = node;
                  scoreTotal.compute(
                          node.item.scoreTotal.countryId,
                          (key, value) -> {
                            if (value == null) {
                              value = 0;
                            }
                            return value + finalNode.item.scoreTotal.score;
                          }
                  );

                  nodePrev.item.lock.unlock();
                  nodePrev = node;
                  node = node.next;
                }

                MsgCountryEntry[] scoreTotalArray = scoreTotal.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getValue))
                        .map(integerIntegerEntry -> new MsgCountryEntry(
                                integerIntegerEntry.getKey().intValue(),
                                integerIntegerEntry.getValue().intValue()))
                        .toArray(MsgCountryEntry[]::new);

                var msg = new MsgCountryLeaderboard(scoreTotalArray);
                synchronized (finalOutputStream) {
                  finalOutputStream.writeObject(msg);
                }

                return null;
              }));
            } else if (object instanceof MsgGetStatusFinal) {
              socketPhaser.arriveAndAwaitAdvance();

              socketPhaser.arriveAndDeregister();
            } else {
              assert false;
            }
          }
        });
        countryIdIter += 1;
      }
    });
    serverThread.start();

    while (true) {
      Future<Void> future = mainFuturesQueue.poll();
      try {
        future.get();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    // join everyone
//    serverThread.join();
//    for (Thread workerThread : workerThreads) {
//      workerThread.join();
//    }
  }
}
