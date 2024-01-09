package server;

import protocol.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

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

    public int getId() {
      return id;
    }

    public int getScore() {
      return score;
    }

    public int getCountryId() {
      return countryId;
    }
  }

  static class MyQueue<T> {
    Queue<T> queue;
    ReentrantLock queueLock;

    Condition canSendCond;
    Condition canRecvCond;

    boolean closed;

    MyQueue() {
      this.queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
      this.queueLock = new ReentrantLock(true);
      this.canSendCond = queueLock.newCondition();
      this.canRecvCond = queueLock.newCondition();
      this.closed = false;
    }

    void close() {
      this.queueLock.lock();
      this.closed = true;
      this.canRecvCond.signalAll();
      this.queueLock.unlock();
    }

    void send(T value) {
      this.queueLock.lock();

      System.out.println(this.queue.size());
      while (this.queue.size() >= MAX_QUEUE_SIZE) {
        try {
          this.canSendCond.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      this.queue.add(value);

      this.canRecvCond.signal();
      this.queueLock.unlock();
    }

    Optional<T> recv() {
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
      this.canSendCond.signal();
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

    public int getId() {
      return id;
    }

    public int getScore() {
      return score;
    }

    public int getCountryId() {
      return countryId;
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

  static class AllLeaderboards {
    ScoreTotal[] competitorLeaderboard;
    MsgCountryEntry[] countryLeaderboard;

    public AllLeaderboards(ScoreTotal[] competitorLeaderboard, MsgCountryEntry[] countryLeaderboard) {
      this.competitorLeaderboard = competitorLeaderboard;
      this.countryLeaderboard = countryLeaderboard;
    }
  }

  static AllLeaderboards computeLeaderboards(LList llist) {
    LListNode nodePrev = llist.head     ; nodePrev.item.lock.lock();
    LListNode node     = llist.head.next;

    Map<Integer, Integer> perCountryScoreTotal = new HashMap<>();
    ArrayList<ScoreTotal> playerScoreTotals = new ArrayList<>();
    while (node != null) {
      node.item.lock.lock();

      LListNode finalNode = node;
      perCountryScoreTotal.compute(
              node.item.scoreTotal.countryId,
              (key, value) -> {
                if (value == null) {
                  value = 0;
                }
                return value + finalNode.item.scoreTotal.score;
              }
      );
      playerScoreTotals.add(node.item.scoreTotal);

      nodePrev.item.lock.unlock();
      nodePrev = node;
      node = node.next;
    }
    playerScoreTotals.sort(
            Comparator.comparingInt(ScoreTotal::getScore)
                    .thenComparing(Comparator.comparingInt(ScoreTotal::getId)));

    MsgCountryEntry[] countryLeaderboard = perCountryScoreTotal.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getValue))
            .map(integerIntegerEntry -> new MsgCountryEntry(
                    integerIntegerEntry.getKey(),
                    integerIntegerEntry.getValue()))
            .toArray(MsgCountryEntry[]::new);
    ScoreTotal[] competitorLeaderboard = playerScoreTotals.toArray(ScoreTotal[]::new);

    return new AllLeaderboards(
            competitorLeaderboard,
            countryLeaderboard
    );
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    // cli
    int p_r = Integer.parseInt(args[0]);
    int p_w = Integer.parseInt(args[1]);
    float delta_t = Float.parseFloat(args[2]);
    String competitorLeaderboardPath = args[3];
    String countryLeaderboardPath = args[4];

    // setup
    MyQueue<ScoreEntry> scoreQueue = new MyQueue();
    Set<Integer> cheaters = new HashSet<>();
    ReentrantLock cheatersLock = new ReentrantLock();
    LList llist = new LList();
    llist.head = new LListNode(new LListItem(null), null);

    CountDownLatch finishedWorking = new CountDownLatch(p_w);

    MyQueue<Future<Void>> mainFuturesQueue = new MyQueue<>();

    // file writer
    Function<AllLeaderboards, Void> writeToFiles = (AllLeaderboards data) -> {
      // competitors
      try (FileWriter fileWriter = new FileWriter(competitorLeaderboardPath)) {
        PrintWriter printWriter = new PrintWriter(fileWriter);
        for (ScoreTotal scoreTotal : data.competitorLeaderboard) {
          printWriter.printf("%d,%d,%d\n", scoreTotal.id, scoreTotal.countryId, scoreTotal.score);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      // country
      try (FileWriter fileWriter = new FileWriter(countryLeaderboardPath)) {
        PrintWriter printWriter = new PrintWriter(fileWriter);
        for (MsgCountryEntry msgCountryEntry : data.countryLeaderboard) {
          printWriter.printf("%d,%d\n", msgCountryEntry.countryId, msgCountryEntry.score);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      return null;
    };

    // workers
    Thread[] workerThreads = new Thread[p_w];
    for (int i = 0; i < p_w; ++i) {
      workerThreads[i] = new Thread(() -> {
        while (true) {
          Optional<ScoreEntry> entryPerhaps = scoreQueue.recv();
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

      final Boolean[] didStartWriter = {false};
      ReentrantLock didStartWriterLock = new ReentrantLock();
      CountDownLatch writerFinished = new CountDownLatch(1);

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

            System.out.println(object.getClass());
            if (object instanceof MsgScoreEntries objectSpec) {
              System.out.println(objectSpec.toString());
              for (MsgScoreEntry msgScoreEntry : objectSpec.msgScoreEntries) {
                System.out.println("hello");
                System.out.println(msgScoreEntry);
                ScoreEntry scoreEntry = new ScoreEntry(
                        msgScoreEntry.id,
                        msgScoreEntry.score,
                        finalCountryIdIter);
                scoreQueue.send(scoreEntry);
              }
              System.out.println("asd");
            } else if (object instanceof MsgGetStatus objectSpec) {
              System.out.println(objectSpec.toString());
              // TODO: delta_t garbage
              ObjectOutputStream finalOutputStream = outputStream;
              mainFuturesQueue.send(new FutureTask<>(() -> {
                AllLeaderboards leaderboards = computeLeaderboards(llist);

                var msg = new MsgCountryLeaderboard(leaderboards.countryLeaderboard);
                synchronized (finalOutputStream) {
                  finalOutputStream.writeObject(msg);
                }

                return null;
              }));
            } else if (object instanceof MsgGetStatusFinal) {
              socketPhaser.arriveAndAwaitAdvance();

              didStartWriterLock.lock();
              if (!didStartWriter[0]) {
                didStartWriter[0] = true;
                Thread thread = new Thread(() -> {
                  AllLeaderboards leaderboards = computeLeaderboards(llist);
                  writeToFiles.apply(leaderboards);
                  writerFinished.countDown();
                });
                thread.start();
              }
              didStartWriterLock.unlock();

              try {
                writerFinished.await();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }

              byte[] competitorLeaderboard = null;
              try {
                competitorLeaderboard = Files.readAllBytes(Path.of(competitorLeaderboardPath));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }

              byte[] countryLeaderboard = null;
              try {
                countryLeaderboard = Files.readAllBytes(Path.of(countryLeaderboardPath));
              } catch (IOException e) {
                  throw new RuntimeException(e);
              }

              try {
                outputStream.writeObject(new MsgFinalStatus(countryLeaderboard, competitorLeaderboard));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }

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
      Optional<Future<Void>> futurePerhaps = mainFuturesQueue.recv();
      if (futurePerhaps.isEmpty()) {
        break;
      }

      Future<Void> future = futurePerhaps.get();
      try {
        future.get();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    // join everyone
    serverThread.join();
    for (Thread workerThread : workerThreads) {
      workerThread.join();
    }
  }
}
