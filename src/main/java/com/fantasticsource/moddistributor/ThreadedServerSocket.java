package com.fantasticsource.moddistributor;

import com.fantasticsource.mctools.MCTools;
import net.minecraft.util.text.TextFormatting;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//Thanks to https://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server
//Thanks to https://stackoverflow.com/questions/41408263/how-to-end-a-thread-handling-socket-connection
//Thanks to https://www.geeksforgeeks.org/difference-between-thread-start-and-thread-run-in-java/

public class ThreadedServerSocket
{
    protected ServerSocket serverSocket;
    protected ArrayList<Socket> clients = new ArrayList<>();

    public ThreadedServerSocket()
    {
        new Thread(() ->
        {
            Socket socket = null;

            try
            {
                serverSocket = new ServerSocket(55565);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }


            while (!serverSocket.isClosed())
            {
                try
                {
                    socket = serverSocket.accept();
                }
                catch (IOException e)
                {
                    if (!(e instanceof SocketException && e.getMessage().equals("socket closed"))) e.printStackTrace();
                }

                // new thread for a client
                clients.add(socket);
                new SendModsToClientThread(socket).start();
            }
        }).start();
    }

    public void close() throws IOException
    {
        serverSocket.close();
        for (Socket socket : clients) socket.close();
    }


    protected static class SendModsToClientThread extends Thread
    {
        protected Socket socket;

        protected SendModsToClientThread(Socket socket)
        {
            this.socket = socket;
            System.out.println(TextFormatting.LIGHT_PURPLE + "Sending mods to " + socket.getInetAddress());
        }

        public void run()
        {
            DataInputStream inputStream;
            BufferedReader reader;
            DataOutputStream out;
            try
            {
                inputStream = new DataInputStream(socket.getInputStream());
                reader = new BufferedReader(new InputStreamReader(inputStream));
                out = new DataOutputStream(socket.getOutputStream());
            }
            catch (IOException e)
            {
                return;
            }


            //Generate lists of filenames to send to client and to remove from client
            File modsDirectory = new File(MCTools.getConfigDir() + ".." + File.separator + "mods");
            File modsClientOnlyDirectory = new File(MCTools.getConfigDir() + ".." + File.separator + "modsClientOnly");
            File modsToRemoveDirectory = new File(MCTools.getConfigDir() + ".." + File.separator + "modsToRemove");

            File[] modFiles = modsDirectory.listFiles(), clientOnlyModFiles = modsClientOnlyDirectory.listFiles(), modFilesToRemove = modsToRemoveDirectory.listFiles();

            HashMap<String, Long> modsToSend = new HashMap<>(), clientOnlyModsToSend = new HashMap<>(), modsToRemove = new HashMap<>();
            try
            {
                if (modFiles != null)
                {
                    for (File file : modFiles)
                    {
                        if (file.isDirectory()) continue;

                        String filename = file.getName();
                        if (!filename.substring(filename.length() - 3).equals("jar")) continue;


                        modsToSend.put(filename, Files.size(file.toPath()));
                    }
                }
                if (clientOnlyModFiles != null)
                {
                    for (File file : clientOnlyModFiles)
                    {
                        if (file.isDirectory()) continue;

                        String filename = file.getName();
                        if (!filename.substring(filename.length() - 3).equals("jar")) continue;


                        clientOnlyModsToSend.put(filename, Files.size(file.toPath()));
                    }
                }
                if (modFilesToRemove != null)
                {
                    for (File file : modFilesToRemove)
                    {
                        if (file.isDirectory()) continue;

                        String filename = file.getName();
                        if (!filename.substring(filename.length() - 3).equals("jar")) continue;


                        modsToRemove.put(filename, Files.size(file.toPath()));
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }


            HashMap<String, Long> existingClientMods = new HashMap<>();
            String line, filename = "";
            byte[] fileBytes;
            long size;
            while (true)
            {
                try
                {
                    line = reader.readLine();
                    if ((line != null) && !line.equals("..."))
                    {
                        if (filename.equals("")) filename = line;
                        else
                        {
                            //Remove any matching mods the client already has from both lists and track them for handling mod removal requests
                            size = Long.parseLong(line);
                            modsToSend.remove(filename, size);
                            clientOnlyModsToSend.remove(filename, size);
                            existingClientMods.put(filename, size);
                            filename = "";
                        }
                    }
                    else
                    {
                        //Remove any mods client doesn't have installed from removal list, then send removal list
                        modsToRemove.entrySet().removeIf(entry -> !entry.getValue().equals(existingClientMods.get(entry.getKey())));

                        out.writeInt(modsToRemove.size());
                        for (Map.Entry<String, Long> entry : modsToRemove.entrySet())
                        {
                            filename = entry.getKey();
                            System.out.println(TextFormatting.LIGHT_PURPLE + "Sending request to remove " + filename + " from " + socket.getInetAddress());
                            out.writeInt(filename.length());
                            out.writeChars(filename);
                        }


                        //Send mods to add
                        for (Map.Entry<String, Long> entry : modsToSend.entrySet())
                        {
                            filename = entry.getKey();
                            System.out.println(TextFormatting.LIGHT_PURPLE + "Sending " + filename + " to " + socket.getInetAddress());
                            out.writeInt(filename.length());
                            out.writeChars(filename);
                            out.writeLong(entry.getValue());
                            File file = new File(modsDirectory.getAbsolutePath() + File.separator + filename);
                            fileBytes = Files.readAllBytes(file.toPath());
                            out.write(fileBytes);
                        }
                        for (Map.Entry<String, Long> entry : clientOnlyModsToSend.entrySet())
                        {
                            filename = entry.getKey();
                            System.out.println(TextFormatting.LIGHT_PURPLE + "Sending " + filename + " to " + socket.getInetAddress());
                            out.writeInt(filename.length());
                            out.writeChars(filename);
                            out.writeLong(entry.getValue());
                            File file = new File(modsClientOnlyDirectory.getAbsolutePath() + File.separator + filename);
                            fileBytes = Files.readAllBytes(file.toPath());
                            out.write(fileBytes);
                        }
                        out.writeInt(0);
                        out.flush();


                        //Close
                        System.out.println(TextFormatting.LIGHT_PURPLE + "Finished sending mods to " + socket.getInetAddress());
                        socket.close();
                        return;
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }


    @Override
    protected void finalize() throws Throwable
    {
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        for (Socket socket : clients)
        {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }
}
