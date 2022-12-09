package com.fantasticsource.moddistributor;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.Tools;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;

public class GuiDownloadingMods extends GuiScreen
{
    public GuiScreen parentScreen;
    public ArrayList<String> textLines = new ArrayList<>();
    public Socket clientSocket = null;

    public GuiDownloadingMods(GuiScreen screen, ServerData serverData)
    {
        parentScreen = screen;

        String ip = serverData.serverIP;
        if (ip.contains(":")) ip = ip.substring(0, ip.indexOf(':'));


        try
        {
            clientSocket = new Socket(ip, 55565);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            File[] files = new File(MCTools.getConfigDir() + ".." + File.separator + "mods").listFiles();
            if (files != null)
            {
                for (File file : files)
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
                    if (!downloadsDirectory.exists()) downloadsDirectory.mkdir();


                    long remaining;
                    String filename;
                    DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                    while (true)
                    {
                        remaining = inputStream.readInt();
                        if (remaining == 0) break;
                        else
                        {
                            filename = "";
                            for (; remaining > 0; remaining--) filename += inputStream.readChar();
                            System.out.println(filename);
                            File file = new File(downloadsDirectory.getAbsolutePath() + File.separator + filename);
                            while (file.exists()) file.delete();
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
                    clientSocket.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
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
        drawCenteredString(fontRenderer, "Downloading mods from server: " + 0 + "%", width / 2, yy, 16777215);
        yy += fontRenderer.FONT_HEIGHT;

        if (textLines != null)
        {
            int maxDisplayable = (height - 5 - yy) / (3 + fontRenderer.FONT_HEIGHT);
            int i = Tools.max(0, textLines.size() - maxDisplayable);

            for (; i < textLines.size(); i++)
            {
                yy += 3 + fontRenderer.FONT_HEIGHT;
                drawCenteredString(fontRenderer, textLines.get(i), width / 2, yy, 11184810);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    @Override
    protected void finalize() throws Throwable
    {
        if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
    }
}
