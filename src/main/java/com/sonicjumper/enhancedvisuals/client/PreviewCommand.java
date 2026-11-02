package com.sonicjumper.enhancedvisuals.client;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public final class PreviewCommand extends CommandBase {
    private static final String[] OPTIONS = { "damage", "explosion", "heartbeat", "drowning", "splash", "rain", "sand", "potion", "clear", "log", "reload" };
    @Override public String getCommandName() { return "evtest"; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/evtest <damage|explosion|heartbeat|drowning|splash|rain|sand|potion|clear>, /evtest log to print hits, /evtest reload for the damage marks file";
    }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !java.util.Arrays.asList(OPTIONS).contains(args[0])) { sender.addChatMessage(new ChatComponentText(getCommandUsage(sender))); return; }
        if (args[0].equals("reload")) {
            ClientEffects.INSTANCE.reloadMarks();
            sender.addChatMessage(new ChatComponentText("Reloaded config/" + DamageMarks.FILE));
        } else if (args[0].equals("log")) {
            sender.addChatMessage(new ChatComponentText(ClientEffects.INSTANCE.toggleLog() ? "Printing every hit you take." : "Stopped printing hits."));
        } else if (!ClientEffects.INSTANCE.active()) {
            sender.addChatMessage(new ChatComponentText("Enable effects and use survival mode, or enable effects in creative mode."));
        } else ClientEffects.INSTANCE.preview(args[0]);
    }
    @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, OPTIONS) : null;
    }
}
