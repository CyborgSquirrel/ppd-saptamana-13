import java.io.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

class MySendDataTask extends TimerTask {
    public ArrayList<ScoreEntry> score_entries;
    public int current_position = 0;
    final int BATCH_SIZE = 20;

    public MySendDataTask(ArrayList<ScoreEntry> score_entries) {
        this.score_entries = score_entries;
    }

    @Override
    public void run() {
        for(int i = current_position; i < current_position + BATCH_SIZE && current_position < score_entries.size(); i++) {
            System.out.println(score_entries.get(i).id + " " + score_entries.get(i).score);
        }
        current_position += BATCH_SIZE;
    }
}

public class Main {
    public static void main(String[] args) {
//        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        int country = Integer.parseInt(args[0]);
        int delta_x = Integer.parseInt(args[1]);
        int number_of_problems = 10;

        String server_address = "localhost";
        int server_port = 6666;

//        try(Socket socket = new Socket(server_address, server_port);
//            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
//
//        } catch(IOException e) {
//            e.printStackTrace();
//        }

        String[] input_file_paths = new String[number_of_problems];

        for(int i = 1; i <= number_of_problems; i++) {
            String formatted_string = String.format("data\\RezultateC%d_P%d.txt", country, i);
            input_file_paths[i - 1] = formatted_string;
        }

        ArrayList<ScoreEntry> score_entries = new ArrayList<>();

        for(int i = 0; i < input_file_paths.length; i++) {
//            int problem_number = Integer.parseInt(input_file_paths[i].substring(input_file_paths[i].lastIndexOf('_') + 1));
            String filePath = input_file_paths[i];
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int id = Integer.parseInt(parts[0].trim());
                    int score = Integer.parseInt(parts[1].trim());

                    ScoreEntry score_entry = new ScoreEntry();
                    score_entry.id = id;
                    score_entry.score = score;

                    score_entries.add(score_entry);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Timer t = new Timer();
        t.schedule(new MySendDataTask(score_entries), 0, delta_x * 1000);
    }
}