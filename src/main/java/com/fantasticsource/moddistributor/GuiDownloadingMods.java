package com.fantasticsource.moddistributor;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.Tools;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.TextFormatting;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;

public class GuiDownloadingMods extends GuiScreen
{
    public static final ArrayList<String> modsToRemove = new ArrayList<>();
    public GuiScreen parentScreen;
    public ArrayList<String> textLines = new ArrayList<>();
    public Socket clientSocket = null;
    public boolean done = false;

    public GuiDownloadingMods(GuiScreen screen, ServerData serverData)
    {
        parentScreen = screen;

        String ip = serverData.serverIP;
        if (ip.contains(":")) ip = ip.substring(0, ip.indexOf(':'));


        try
        {
            File modsDirectory = new File(MCTools.getConfigDir() + ".." + File.separator + "mods");

            clientSocket = new Socket(ip, 55565);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            File[] modFiles = modsDirectory.listFiles();
            if (modFiles != null)
            {
                for (File file : modFiles)
                {
                    if (file.isDirectory()) continue;

                    String filename = file.getName();
                    if (!filename.substring(filename.length() - 3).equals("jar")) continue;


                    writer.write(filename + "\r\n");
                    writer.write(Files.size(file.toPath()) + "\r\n");
                }
            }
            writer.write("...\r\n");
            writer.flush();


            new Thread(() ->
            {
                try
                {
                    File downloadsDirectory = new File(MCTools.getConfigDir() + ".." + File.separator + "modsDownloading");
                    if (downloadsDirectory.exists()) Tools.deleteFilesRecursively(downloadsDirectory);
                    downloadsDirectory.mkdir();


                    long remaining;
                    String filename;
                    DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());

                    //Populate mod removal list
                    for (int fileRemovalCount = inputStream.readInt(); fileRemovalCount > 0; fileRemovalCount--)
                    {
                        remaining = inputStream.readInt();
                        filename = "";
                        for (; remaining > 0; remaining--) filename += inputStream.readChar();
                        modsToRemove.add(filename);
                    }


                    //Receive new mod files
                    while (true)
                    {
                        remaining = inputStream.readInt();
                        if (remaining == 0) break;
                        else
                        {
                            filename = "";
                            for (; remaining > 0; remaining--) filename += inputStream.readChar();
                            File file = new File(downloadsDirectory.getAbsolutePath() + File.separator + filename);
                            textLines.add(file.getName());
                            while (file.exists()) file.delete(); //Delete from downloads folder if it already exists there
                            FileOutputStream outputStream = new FileOutputStream(file);

                            remaining = inputStream.readLong();
                            while (remaining > 0)
                            {
                                outputStream.write(inputStream.read());
                                remaining--;
                            }
                            outputStream.close();
                        }
                    }


                    //Handle files
                    if (modsToRemove.size() > 0)
                    {
                        //Create batch files to...
                        //...delete jars when possible
                        //...then move new jars
                        //...then delete themselves
                        //and run them
                        File file = new File(modsDirectory + File.separator + "delete_when_possible.bat");
                        while (file.exists()) file.delete();
                        BufferedWriter writer2 = new BufferedWriter(new FileWriter(file));
                        writer2.write("@echo off\r\n" +
                                ":loop > nul 2> nul\r\n" +
                                "del %1 > nul 2> nul\r\n" +
                                "if exist %1 goto loop > nul 2> nul\r\n" +
                                "exit > nul 2> nul\r\n");
                        writer2.close();


                        file = new File(modsDirectory + File.separator + "mods to delete.txt");
                        while (file.exists()) file.delete();
                        writer2 = new BufferedWriter(new FileWriter(file));
                        for (String s : modsToRemove) writer2.write(s + "\r\n");
                        writer2.close();


                        file = new File("delete_multiple_when_possible.bat");
                        while (file.exists()) file.delete();
                        writer2 = new BufferedWriter(new FileWriter(file));
                        writer2.write("@echo off\r\n" +
                                "cd mods > nul 2> nul\r\n" +
                                "for /F \"usebackq tokens=*\" %%A in (\"mods to delete.txt\") do start /min /wait delete_when_possible.bat \"%%A\" > nul 2> nul\r\n" +
                                "del delete_when_possible.bat > nul 2> nul\r\n" +
                                "del " + '"' + "mods to delete.txt" + '"' + " > nul 2> nul\r\n" +
                                "cd .. > nul 2> nul\r\n" +
                                "move modsDownloading" + File.separator + "* mods > nul 2> nul\r\n" +
                                "del delete_multiple_when_possible.bat > nul 2> nul & exit > nul 2> nul\r\n");
                        writer2.close();

                        Runtime.getRuntime().exec("cmd /c start /min " + file.getName());
                    }
                    else
                    {
                        //In this case, the batch files aren't moving the jars later, and we don't have anything preventing us from moving them now, so move them
                        File[] files = downloadsDirectory.listFiles();
                        if (files != null)
                        {
                            for (File file : files)
                            {
                                Files.move(file.toPath(), new File(modsDirectory.getAbsolutePath() + File.separator + file.getName()).toPath());
                            }
                        }
                    }


                    //Close
                    clientSocket.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }


                done = true;
            }).start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void initGui()
    {
        buttonList.clear();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        drawDefaultBackground();

        int yy = 5;
        if (!done) drawCenteredString(fontRenderer, "Downloading mods from server...", width / 2, yy, 16777215);
        else
        {
            drawCenteredString(fontRenderer, TextFormatting.YELLOW + "Done! Restart the game to finish the update.", width / 2, yy, 16777215);
            yy += 3 + fontRenderer.FONT_HEIGHT;
            drawCenteredString(fontRenderer, TextFormatting.RED + "DO NOT CLOSE COMMAND PROMPT WINDOWS!!!", width / 2, yy, 16777215);
        }
        yy += 3 + fontRenderer.FONT_HEIGHT;

        int maxDisplayable = (height - 5 - yy) / (3 + fontRenderer.FONT_HEIGHT);
        for (int i = Tools.max(0, textLines.size() - maxDisplayable); i < textLines.size(); i++)
        {
            yy += 3 + fontRenderer.FONT_HEIGHT;
            drawCenteredString(fontRenderer, TextFormatting.GRAY + textLines.get(i), width / 2, yy, 11184810);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    @Override
    protected void finalize() throws Throwable
    {
        if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
    }
}
