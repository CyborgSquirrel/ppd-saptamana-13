package client;

import protocol.MsgCountryLeaderboard;
import protocol.MsgGetStatus;
import protocol.MsgScoreEntries;
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
    final int BATCH_SIZE = 200;
    public ObjectOutputStream output_stream;
    public ObjectInputStream input_stream;

    public MySendDataTask(ArrayList<MsgScoreEntry> score_entries, ObjectOutputStream output_stream, ObjectInputStream input_stream) {
        this.score_entries = score_entries;
        this.output_stream = output_stream;
        this.input_stream = input_stream;
    }

    @Override
    public void run() {
        System.out.format("Pos = %d/%d\n", start_pos, score_entries.size());

        int score_entries_batch_len = Math.min(score_entries.size() - start_pos, BATCH_SIZE);

        MsgScoreEntry[] score_entries_batch = new MsgScoreEntry[score_entries_batch_len];
        int i;
        for (i = 0; i < score_entries_batch_len; i++) {
            score_entries_batch[i] = score_entries.get(start_pos + i);
        }
        System.out.format("Sending %d entries\n", i);
        try{
            MsgScoreEntries score_entries_send_batch = new MsgScoreEntries(score_entries_batch);
            output_stream.writeObject(score_entries_send_batch);
            System.out.format("Sending %d entries succeeded\n\n", i);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        start_pos += i;
//        start_pos += BATCH_SIZE;
        if(start_pos == score_entries.size()) {
            System.out.println("Reached end: " + start_pos + "/" + score_entries.size());
            System.out.println("Sending MsgGetStatus...");
            try {
                output_stream.writeObject(new MsgGetStatus());
                System.out.println("Succeeded sending MsgGetStatus...");
            } catch(IOException ex){
                System.out.println("Error in sending MsgGetStatus");
                ex.printStackTrace();
            }

            Object object = null;
            try {
                object = input_stream.readObject();
            } catch(IOException | ClassNotFoundException ex) {
                System.out.println("Error reading MsgGetStatus response");
                ex.printStackTrace();
            }
            if(object instanceof MsgCountryLeaderboard object_spec) {
                System.out.format("Leaderboard received. Size = %d\n", object_spec.entries.length);
            }
            cancel();
        }
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
            String formatted_string = String.format("%s/RezultateC%d_P%d.txt", folder_path, country, i);
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
        t.schedule(new MySendDataTask(score_entries, output_stream, input_stream), 0, delta_x * 1000);
    }
}