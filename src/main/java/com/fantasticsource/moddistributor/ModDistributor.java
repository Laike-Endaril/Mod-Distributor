package com.fantasticsource.moddistributor;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.ReflectionTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.LanServerInfo;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Mod(modid = ModDistributor.MODID, name = ModDistributor.NAME, version = ModDistributor.VERSION, dependencies = "required-after:fantasticlib@[1.12.2.047c,)")
public class ModDistributor
{
    public static final String MODID = "moddistributor";
    public static final String NAME = "Mod Distributor";
    public static final String VERSION = "1.12.2.000b";

    public static ThreadedServerSocket threadedServerSocket = null;

    public ModDistributor()
    {
        File file = new File(MCTools.getConfigDir() + ".." + File.separator + "modsClientOnly");
        if (!file.exists()) file.mkdir();
        file = new File(MCTools.getConfigDir() + ".." + File.separator + "modsToRemove");
        if (!file.exists()) file.mkdir();
    }

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(ModDistributor.class);
    }

    @SubscribeEvent
    public static void saveConfig(ConfigChangedEvent.OnConfigChangedEvent event)
    {
        if (event.getModID().equals(MODID)) ConfigManager.sync(MODID, Config.Type.INSTANCE);
    }


    @Mod.EventHandler
    public static void serverStart(FMLServerStartedEvent event) throws IOException
    {
        if (threadedServerSocket != null) threadedServerSocket.close();
        threadedServerSocket = new ThreadedServerSocket();
    }

    @Mod.EventHandler
    public static void serverStop(FMLServerStoppingEvent event) throws IOException
    {
        threadedServerSocket.close();
        threadedServerSocket = null;
    }


    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void addDownloadButton(GuiScreenEvent.InitGuiEvent.Post event)
    {
        if (!(event.getGui() instanceof GuiMultiplayer)) return;


        GuiMultiplayer guiMultiplayer = (GuiMultiplayer) event.getGui();
        List<GuiButton> buttonList = event.getButtonList();
        buttonList.get(0).width = 68;
        buttonList.get(1).width = 68;
        buttonList.get(5).width = 68;
        buttonList.get(6).width = 68;
        GuiButton downloadButton = new GuiButton(5, guiMultiplayer.width / 2 - 34 - 4 - 68 - 4 - 68, guiMultiplayer.height - 28, 68, 20, "Download");
        buttonList.get(0).x = guiMultiplayer.width / 2 - 34 - 4 - 68;
        buttonList.get(1).x = guiMultiplayer.width / 2 - 34;
        buttonList.get(5).x = guiMultiplayer.width / 2 + 34 + 4;
        buttonList.get(6).x = guiMultiplayer.width / 2 + 34 + 4 + 68 + 4;

        downloadButton.enabled = false;
        buttonList.add(downloadButton);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void refreshDownloadButton(GuiScreenEvent event)
    {
        if (!(event.getGui() instanceof GuiMultiplayer)) return;


        GuiMultiplayer guiMultiplayer = (GuiMultiplayer) event.getGui();
        List<GuiButton> buttonList = (List<GuiButton>) ReflectionTool.get(ReflectionTool.getField(GuiScreen.class, "field_146292_n", "buttonList"), guiMultiplayer);
        if (buttonList != null && buttonList.size() >= 8)
        {
            buttonList.get(7).enabled = buttonList.get(2).enabled;
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void requestDownload(GuiScreenEvent.ActionPerformedEvent.Post event)
    {
        if (!(event.getGui() instanceof GuiMultiplayer)) return;


        GuiButton button = event.getButton();
        if (button.enabled && button.displayString.equals("Download"))
        {
            GuiMultiplayer guiMultiplayer = (GuiMultiplayer) event.getGui();
            ServerSelectionList serverListSelector = (ServerSelectionList) ReflectionTool.get(ReflectionTool.getField(GuiMultiplayer.class, "field_146803_h", "serverListSelector"), guiMultiplayer);
            GuiListExtended.IGuiListEntry guiListEntry = serverListSelector.getSelected() < 0 ? null : serverListSelector.getListEntry(serverListSelector.getSelected());

            ServerData serverData = null;
            if (guiListEntry instanceof ServerListEntryNormal)
            {
                serverData = ((ServerListEntryNormal) guiListEntry).getServerData();
            }
            else if (guiListEntry instanceof ServerListEntryLanDetected)
            {
                LanServerInfo lanserverinfo = ((ServerListEntryLanDetected) guiListEntry).getServerData();
                serverData = new ServerData(lanserverinfo.getServerMotd(), lanserverinfo.getServerIpPort(), true);
            }

            if (serverData != null) Minecraft.getMinecraft().displayGuiScreen(new GuiDownloadingMods(guiMultiplayer, serverData));
        }
    }
}
