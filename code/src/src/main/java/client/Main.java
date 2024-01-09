package client;

import protocol.MsgScoreEntry;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

class MySendDataTask extends TimerTask {
    public ArrayList<MsgScoreEntry> score_entries;
    public int start_pos = 0;
    final int BATCH_SIZE = 20;
    public ObjectOutputStream output_stream;

    public MySendDataTask(ArrayList<MsgScoreEntry> score_entries, ObjectOutputStream output_stream) {
        this.score_entries = score_entries;
        this.output_stream = output_stream;
    }

    @Override
    public void run() {
        MsgScoreEntry[] score_entries_batch = new MsgScoreEntry[20];
        for (int i = 0; i < BATCH_SIZE && start_pos + i < score_entries.size(); i++) {
            score_entries_batch[i] = score_entries.get(start_pos + i);
        }
        try{
            output_stream.writeObject(score_entries_batch);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        start_pos += BATCH_SIZE;
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        int country = Integer.parseInt(args[0]);
        int delta_x = Integer.parseInt(args[1]);
        String folder_path = args[2];
        int number_of_problems = 10;

        String server_address = "localhost";
        int server_port = 42069;

        Socket socket = null;
        ObjectInputStream input_stream = null;
        ObjectOutputStream output_stream = null;
        System.out.format("Connecting to %s:%d\n", server_address, server_port);
        try{
            socket = new Socket(server_address, server_port);
            output_stream = new ObjectOutputStream(socket.getOutputStream());
            input_stream = new ObjectInputStream(socket.getInputStream());
            System.out.println("Connection succeeded!");
        } catch(IOException e) {
            System.out.println("Connection failed!");
            e.printStackTrace();
        }

        String[] input_file_paths = new String[number_of_problems];

        for(int i = 1; i <= number_of_problems; i++) {
            String formatted_string = String.format("%s\\RezultateC%d_P%d.txt", folder_path, country, i);
            input_file_paths[i - 1] = formatted_string;
        }

        ArrayList<MsgScoreEntry> score_entries = new ArrayList<>();

        for(int i = 0; i < input_file_paths.length; i++) {
//            int problem_number = Integer.parseInt(input_file_paths[i].substring(input_file_paths[i].lastIndexOf('_') + 1));
            String filePath = input_file_paths[i];
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    int id = Integer.parseInt(parts[0].trim());
                    int score = Integer.parseInt(parts[1].trim());

                    MsgScoreEntry score_entry = new MsgScoreEntry(id, score);

                    score_entries.add(score_entry);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Timer t = new Timer();
        t.schedule(new MySendDataTask(score_entries, output_stream), 0, delta_x * 1000);
    }
}