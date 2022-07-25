package dev.drawethree.ultraprisoncore.gangs.commands.impl.admin;

import dev.drawethree.ultraprisoncore.gangs.commands.GangCommand;
import dev.drawethree.ultraprisoncore.gangs.commands.GangSubCommand;
import dev.drawethree.ultraprisoncore.gangs.model.Gang;
import dev.drawethree.ultraprisoncore.gangs.utils.GangsConstants;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public final class GangAdminRenameSubCommand extends GangSubCommand {

	public GangAdminRenameSubCommand(GangCommand command) {
		super(command, "rename");
	}

	@Override
	public boolean execute(CommandSender sender, List<String> args) {
		if (args.size() == 2) {
			String oldName = args.get(0);
			String newName = args.get(1);
			return this.command.getPlugin().getGangsManager().forceRename(sender, oldName, newName);
		}
		return false;
	}

	@Override
	public String getUsage() {
		return ChatColor.RED + "/gang admin rename <gang> <new_name>";
	}

	@Override
	public boolean canExecute(CommandSender sender) {
		return sender.hasPermission(GangsConstants.GANGS_ADMIN_PERM);
	}

	@Override
	public List<String> getTabComplete() {
		return this.command.getPlugin().getGangsManager().getAllGangs().stream().map(Gang::getName).collect(Collectors.toList());
	}
}