package com.DreamLong.utility_plugin;

import org.bukkit.Bukkit;
import org.bukkit.BanEntry;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A Minecraft plugin that requires players to log in before they can
 * access certain utility commands. All command logic is contained within
 * this single class using private inner classes.
 */
public class UtilityPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final Map<UUID, PlayerData> playersData = new HashMap<>();
    private final Map<UUID, BukkitTask> loginTasks = new HashMap<>();
    private final Map<UUID, Location> lastKnownLocations = new HashMap<>();
    private File dataFolder;

    private final String prefix = ChatColor.GRAY + "[" + ChatColor.AQUA + "Utility" + ChatColor.GRAY + "] " + ChatColor.RESET;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("ss/HH/dd/yyyy");

    @Override
    public void onEnable() {
        // Create the plugin data folder if it doesn't exist
        this.dataFolder = new File(getDataFolder(), "ultility_plugin");
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
        loadAllData();
        
        // Load the default config.yml
        this.saveDefaultConfig();

        // Register this class as a listener for events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register the command executors as private inner classes
        this.getCommand("login").setExecutor(new LoginCommandExecutor());
        this.getCommand("register").setExecutor(new RegisterCommandExecutor());
        this.getCommand("ban").setExecutor(new BanCommandExecutor("ban"));
        this.getCommand("unban").setExecutor(new UnbanCommandExecutor());
        this.getCommand("tempban").setExecutor(new BanCommandExecutor("tempban"));
        this.getCommand("kick").setExecutor(new KickCommandExecutor());
        this.getCommand("mute").setExecutor(new MuteCommandExecutor("mute"));
        this.getCommand("unmute").setExecutor(new MuteCommandExecutor("unmute"));
        this.getCommand("warn").setExecutor(new WarnCommandExecutor());
        this.getCommand("unwarn").setExecutor(new UnwarnCommandExecutor());
        this.getCommand("checkwarn").setExecutor(new CheckCommandExecutor("warn"));
        this.getCommand("checkban").setExecutor(new CheckCommandExecutor("ban"));
        this.getCommand("checkmute").setExecutor(new CheckCommandExecutor("mute"));
        
        // Register the new command to set the login location
        this.getCommand("setlogin").setExecutor(new SetLoginCommandExecutor());

        getLogger().info("UtilityPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        saveAllData();
        // Clear the in-memory data on disable
        loggedInPlayers.clear();
        playersData.clear();
        // Cancel all pending login tasks
        for (BukkitTask task : loginTasks.values()) {
            task.cancel();
        }
        loginTasks.clear();
        getLogger().info("UtilityPlugin has been disabled!");
    }
    
    public void loadAllData() {
        File[] playerFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null) {
            getLogger().info("No player data files found.");
            return;
        }

        JSONParser parser = new JSONParser();
        for (File file : playerFiles) {
            try (FileReader reader = new FileReader(file)) {
                JSONObject data = (JSONObject) parser.parse(reader);
                String uuidString = file.getName().replace(".json", "");
                UUID playerUUID = UUID.fromString(uuidString);

                PlayerData playerData = new PlayerData();
                playerData.username = (String) data.get("Username");
                playerData.passwordHash = (String) data.get("Password");
                
                playerData.warns = (long) data.getOrDefault("Warns", 0L);
                playerData.startWarns = (String) data.get("StartWarns");
                
                playerData.mutes = (long) data.getOrDefault("Mutes", 0L);
                playerData.startMutes = (String) data.get("StartMutes");

                playerData.ban = (long) data.getOrDefault("Ban", 0L);
                playerData.startBans = (String) data.get("StartBans");

                // New field for login attempts
                playerData.loginAttempts = (long) data.getOrDefault("LoginAttempts", 0L);
                
                playersData.put(playerUUID, playerData);
            } catch (IOException | ParseException e) {
                getLogger().warning("Failed to load data from file " + file.getName() + ": " + e.getMessage());
            }
        }
        getLogger().info("Successfully loaded data for " + playersData.size() + " players.");
    }
    
    public void saveAllData() {
        for (UUID uuid : playersData.keySet()) {
            savePlayerData(uuid);
        }
    }
    
    public void savePlayerData(UUID uuid) {
        PlayerData playerData = playersData.get(uuid);
        if (playerData == null) {
            return;
        }
        
        File playerFile = new File(dataFolder, uuid.toString() + ".json");
        JSONObject data = new JSONObject();
        data.put("Username", playerData.username);
        data.put("Password", playerData.passwordHash);
        data.put("Warns", playerData.warns);
        data.put("StartWarns", playerData.startWarns);
        data.put("Mutes", playerData.mutes);
        data.put("StartMutes", playerData.startMutes);
        data.put("Ban", playerData.ban);
        data.put("StartBans", playerData.startBans);
        data.put("LoginAttempts", playerData.loginAttempts); // Save login attempts

        try (FileWriter fileWriter = new FileWriter(playerFile)) {
            fileWriter.write(data.toJSONString());
            fileWriter.flush();
        } catch (IOException e) {
            getLogger().warning("Failed to save data for player " + playerData.username + ": " + e.getMessage());
        }
    }

    private Location getLoginLocation() {
        String worldName = getConfig().getString("login-spawn.world", "world");
        double x = getConfig().getDouble("login-spawn.x", 0);
        double y = getConfig().getDouble("login-spawn.y", 100);
        double z = getConfig().getDouble("login-spawn.z", 0);
        float yaw = (float) getConfig().getDouble("login-spawn.yaw", 0);
        float pitch = (float) getConfig().getDouble("login-spawn.pitch", 0);
        
        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerData playerData = getPlayerData(playerUUID);
        
        // Save the player's current location before teleporting them
        lastKnownLocations.put(playerUUID, player.getLocation());
        
        // Teleport the player to the configured safe spawn location for login
        player.teleport(getLoginLocation());
        
        // Check login attempts for progressive punishment
        if (playerData.loginAttempts == 5) {
            player.kickPlayer(ChatColor.RED + "You have been kicked for 5 failed login attempts.\nPlease re-join and try again.");
            return;
        } else if (playerData.loginAttempts == 6) {
            Date expiry = new Date(System.currentTimeMillis() + 5 * 60 * 1000); // 5 minutes
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), "Too many failed login attempts (5 min ban)", expiry, "UtilityPlugin");
            player.kickPlayer(ChatColor.RED + "You have been banned for 5 minutes due to too many failed login attempts.");
            return;
        } else if (playerData.loginAttempts >= 7) {
            Date expiry = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000); // 24 hours
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), "Too many failed login attempts (24h ban)", expiry, "UtilityPlugin");
            player.kickPlayer(ChatColor.RED + "You have been banned for 24 hours due to too many failed login attempts.");
            return;
        }

        if (playerData.passwordHash == null) {
            player.sendMessage(prefix + ChatColor.YELLOW + "You must register an account to access commands!");
            player.sendMessage(prefix + ChatColor.YELLOW + "Type " + ChatColor.GOLD + "/register <password> <confirm_password>" + ChatColor.YELLOW + " to continue.");
        } else {
            player.sendMessage(prefix + ChatColor.YELLOW + "You must log in to access commands!");
            player.sendMessage(prefix + ChatColor.YELLOW + "Type " + ChatColor.GOLD + "/login <password>" + ChatColor.YELLOW + " to continue.");
        }

        // Start a timed task to remind players to log in and kick them after a minute
        BukkitTask loginTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!isLoggedIn(playerUUID)) {
                if (playerData.passwordHash == null) {
                    player.sendMessage(prefix + ChatColor.YELLOW + "Please register using " + ChatColor.GOLD + "/register <password> <confirm_password>");
                } else {
                    player.sendMessage(prefix + ChatColor.YELLOW + "Please log in using " + ChatColor.GOLD + "/login <password>");
                }
            }
        }, 40L, 40L); // 40 ticks = 2 seconds

        BukkitTask kickTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isLoggedIn(playerUUID)) {
                player.kickPlayer(ChatColor.RED + "You have been kicked for inactivity.\nPlease log in to play on the server.");
            }
        }, 1200L); // 1200 ticks = 60 seconds (1 minute)

        loginTasks.put(playerUUID, loginTask);
        loginTasks.put(playerUUID, kickTask);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerData(event.getPlayer().getUniqueId());
        loggedInPlayers.remove(event.getPlayer().getUniqueId());
        BukkitTask task = loginTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    // This event prevents players from running any commands until they log in
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().split(" ")[0].substring(1);

        // Allow the /login, /register, and /setlogin commands to be used
        if (command.equalsIgnoreCase("login") || command.equalsIgnoreCase("register") || command.equalsIgnoreCase("setlogin")) {
            return;
        }

        // Check if the player is logged in. If not, cancel the command.
        if (!isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(prefix + ChatColor.RED + "You must be logged in to use this command.");
            player.sendMessage(prefix + ChatColor.RED + "Type " + ChatColor.GOLD + "/login <password>" + ChatColor.RED + " to continue.");
        }
    }

    // Prevents muted players from chatting
    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        PlayerData playerData = playersData.get(event.getPlayer().getUniqueId());
        if (playerData != null && playerData.isMuted()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(prefix + ChatColor.RED + "You are muted and cannot chat.");
        }
    }

    /**
     * Checks if a player's UUID is in the logged-in set.
     * @param playerUUID The UUID of the player.
     * @return true if the player is logged in, false otherwise.
     */
    public boolean isLoggedIn(UUID playerUUID) {
        return loggedInPlayers.contains(playerUUID);
    }

    private PlayerData getPlayerData(UUID uuid) {
        return playersData.computeIfAbsent(uuid, k -> new PlayerData());
    }
    
    private String getSha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            getLogger().severe("SHA-256 algorithm not found!");
            return null;
        }
    }

    private static class PlayerData {
        public String username;
        public String passwordHash;
        public long warns = 0;
        public String startWarns;
        public long mutes = 0;
        public String startMutes;
        public long ban = 0;
        public String startBans;
        public long loginAttempts = 0;
        
        public boolean isMuted() {
            if (mutes <= 0) {
                return false;
            }
            if (mutes == -1) {
                return true;
            }
            try {
                Date startDate = dateFormat.parse(startMutes);
                long elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(new Date().getTime() - startDate.getTime());
                return elapsedMinutes < mutes;
            } catch (java.text.ParseException e) {
                return false;
            }
        }
    }

    /**
     * Handles the /register command.
     */
    private class RegisterCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            PlayerData playerData = getPlayerData(player.getUniqueId());
            
            if (playerData.passwordHash != null) {
                player.sendMessage(prefix + ChatColor.RED + "You are already registered!");
                return true;
            }
            
            if (args.length != 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /register <password> <confirm_password>");
                return true;
            }
            
            String password = args[0];
            String confirmPassword = args[1];

            if (!password.equals(confirmPassword)) {
                player.sendMessage(prefix + ChatColor.RED + "Passwords do not match. Please try again.");
                return true;
            }
            
            String passwordHash = getSha256Hash(password);
            
            if (passwordHash == null) {
                player.sendMessage(prefix + ChatColor.RED + "Failed to hash your password. Please contact an admin.");
                return true;
            }
            
            playerData.username = player.getName();
            playerData.passwordHash = passwordHash;
            playerData.loginAttempts = 0; // Reset login attempts on successful registration
            savePlayerData(player.getUniqueId());
            
            player.sendMessage(prefix + ChatColor.GREEN + "You have successfully registered!");
            player.sendMessage(prefix + ChatColor.GREEN + "You can now log in with " + ChatColor.GOLD + "/login <password>" + ChatColor.GREEN + ".");
            
            return true;
        }
    }

    /**
     * Handles the /login command.
     */
    private class LoginCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();
            PlayerData playerData = playersData.get(playerUUID);

            if (isLoggedIn(playerUUID)) {
                player.sendMessage(prefix + ChatColor.GREEN + "You are already logged in!");
                return true;
            }
            
            if (playerData == null) {
                player.sendMessage(prefix + ChatColor.RED + "You are not registered! Please register with /register <password>.");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /login <password>");
                return true;
            }
            
            String password = args[0];
            String enteredPasswordHash = getSha256Hash(password);
            
            if (enteredPasswordHash == null) {
                 player.sendMessage(prefix + ChatColor.RED + "Failed to hash your password. Please contact an admin.");
                 return true;
            }
            
            if (enteredPasswordHash.equals(playerData.passwordHash)) {
                loggedInPlayers.add(playerUUID);
                // Reset login attempts on successful login
                playerData.loginAttempts = 0;
                savePlayerData(player.getUniqueId());
                player.sendMessage(prefix + ChatColor.GREEN + "You have successfully logged in!");
                player.sendMessage(prefix + ChatColor.GREEN + "You can now use other commands.");
                
                // Teleport the player back to their original location
                Location lastLocation = lastKnownLocations.get(playerUUID);
                if (lastLocation != null) {
                    player.teleport(lastLocation);
                    lastKnownLocations.remove(playerUUID); // Clean up the map
                }

                // Cancel the timed login and kick tasks
                BukkitTask task = loginTasks.remove(playerUUID);
                if (task != null) {
                    task.cancel();
                }
            } else {
                playerData.loginAttempts++; // Increment attempt on incorrect password
                savePlayerData(player.getUniqueId());
                player.sendMessage(prefix + ChatColor.RED + "Incorrect password.");
            }
            
            return true;
        }
    }

    /**
     * A generic command executor for /ban and /tempban.
     * It checks if the player is logged in and has the required permission.
     */
    private class BanCommandExecutor implements CommandExecutor {
        private final String commandName;

        public BanCommandExecutor(String commandName) {
            this.commandName = commandName;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /" + label + " <player> <reason>");
                return false;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target == null || target.getName() == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found.");
                return true;
            }

            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            PlayerData targetData = getPlayerData(target.getUniqueId());

            if (commandName.equalsIgnoreCase("ban")) {
                targetData.ban = -1;
                targetData.startBans = dateFormat.format(new Date());
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(targetName, reason, null, player.getName());
                Bukkit.broadcastMessage(prefix + ChatColor.RED + targetName + ChatColor.YELLOW + " has been banned by " + ChatColor.GREEN + player.getName() + ChatColor.YELLOW + " for " + ChatColor.RED + reason + ChatColor.YELLOW + ".");
                if (target.isOnline()) {
                    ((Player) target).kickPlayer(ChatColor.RED + "You have been banned.\nReason: " + reason);
                }
            } else if (commandName.equalsIgnoreCase("tempban")) {
                long durationMinutes = 3600;
                targetData.ban = durationMinutes;
                targetData.startBans = dateFormat.format(new Date());
                Date expiry = new Date(System.currentTimeMillis() + durationMinutes * 60 * 1000);
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(targetName, reason, expiry, player.getName());
                Bukkit.broadcastMessage(prefix + ChatColor.RED + targetName + ChatColor.YELLOW + " has been temporarily banned by " + ChatColor.GREEN + player.getName() + ChatColor.YELLOW + " for " + ChatColor.RED + reason + ChatColor.YELLOW + ".");
                if (target.isOnline()) {
                    ((Player) target).kickPlayer(ChatColor.RED + "You have been temporarily banned.\nReason: " + reason);
                }
            }
            savePlayerData(target.getUniqueId());
            return true;
        }
    }
    
    /**
     * Handles the /unban command.
     */
    private class UnbanCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /unban <player> <reason>");
                return false;
            }

            String targetName = args[0];
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            
            BanEntry banEntry = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntry(targetName);
            if (banEntry != null) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(targetName);
                sender.sendMessage(prefix + ChatColor.GREEN + "Player " + ChatColor.YELLOW + targetName + ChatColor.GREEN + " has been unbanned. Reason: " + ChatColor.WHITE + reason);
                Bukkit.broadcastMessage(prefix + ChatColor.GREEN + targetName + ChatColor.YELLOW + " has been unbanned by " + ChatColor.GREEN + player.getName() + ChatColor.YELLOW + " for " + ChatColor.RED + reason + ChatColor.YELLOW + ".");
            } else {
                sender.sendMessage(prefix + ChatColor.YELLOW + "Player " + targetName + " is not currently banned.");
            }
            return true;
        }
    }

    /**
     * Handles the /kick command.
     */
    private class KickCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /kick <player> <reason>");
                return false;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found.");
                return true;
            }

            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

            target.kickPlayer(ChatColor.RED + "You have been kicked.\nReason: " + reason);
            Bukkit.broadcastMessage(prefix + ChatColor.RED + targetName + ChatColor.YELLOW + " has been kicked by " + ChatColor.GREEN + player.getName() + ChatColor.YELLOW + " for " + ChatColor.RED + reason + ChatColor.YELLOW + ".");

            return true;
        }
    }

    /**
     * Handles the /mute and /unmute commands.
     */
    private class MuteCommandExecutor implements CommandExecutor {
        private final String commandName;

        public MuteCommandExecutor(String commandName) {
            this.commandName = commandName;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /" + label + " <player> <reason>");
                return false;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found.");
                return true;
            }
            
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            
            PlayerData targetData = getPlayerData(target.getUniqueId());

            if (commandName.equalsIgnoreCase("mute")) {
                if (targetData.isMuted()) {
                    player.sendMessage(prefix + ChatColor.RED + targetName + " is already muted.");
                } else {
                    targetData.mutes = 12; // Mute for 12 minutes as an example
                    targetData.startMutes = dateFormat.format(new Date());
                    player.sendMessage(prefix + ChatColor.GREEN + "You have muted " + ChatColor.RED + targetName + ChatColor.GREEN + " for: " + ChatColor.YELLOW + reason + ".");
                    target.sendMessage(prefix + ChatColor.RED + "You have been muted by a staff member and cannot chat. Reason: " + ChatColor.WHITE + reason);
                }
            } else if (commandName.equalsIgnoreCase("unmute")) {
                if (!targetData.isMuted()) {
                    player.sendMessage(prefix + ChatColor.RED + targetName + " is not muted.");
                } else {
                    targetData.mutes = 0;
                    targetData.startMutes = "";
                    player.sendMessage(prefix + ChatColor.GREEN + "You have unmuted " + ChatColor.RED + targetName + ChatColor.GREEN + ". Reason: " + ChatColor.YELLOW + reason + ".");
                    target.sendMessage(prefix + ChatColor.GREEN + "You have been unmuted and can now chat. Reason: " + ChatColor.WHITE + reason);
                }
            }
            savePlayerData(target.getUniqueId());
            return true;
        }
    }
    
    /**
     * Handles the /warn command.
     */
    private class WarnCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }
            
            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /warn <player> <reason>");
                return false;
            }
            
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found or is offline.");
                return true;
            }
            
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            
            PlayerData targetData = getPlayerData(target.getUniqueId());
            targetData.warns++;
            targetData.startWarns = dateFormat.format(new Date());

            player.sendMessage(prefix + ChatColor.GREEN + "You have warned " + ChatColor.YELLOW + targetName + ChatColor.GREEN + " for: " + ChatColor.RED + reason);
            target.sendMessage(prefix + ChatColor.RED + "You have been warned by a staff member. Total warnings: " + targetData.warns);
            
            savePlayerData(target.getUniqueId());
            return true;
        }
    }
    
    /**
     * Handles the /unwarn command.
     */
    private class UnwarnCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }
            
            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /unwarn <player> <reason>");
                return false;
            }
            
            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target == null || target.getName() == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found.");
                return true;
            }
            
            PlayerData targetData = playersData.get(target.getUniqueId());
            if (targetData == null) {
                player.sendMessage(prefix + ChatColor.YELLOW + "No data found for player " + targetName + ".");
                return true;
            }
            
            if (targetData.warns > 0) {
                targetData.warns--;
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                player.sendMessage(prefix + ChatColor.GREEN + "You have unwarned " + ChatColor.YELLOW + targetName + ChatColor.GREEN + ". New warnings count: " + targetData.warns + ". Reason: " + ChatColor.RED + reason);
                if (target.isOnline()) {
                     ((Player) target).sendMessage(prefix + ChatColor.GREEN + "A staff member has removed one of your warnings. New total warnings: " + targetData.warns);
                }
                savePlayerData(target.getUniqueId());
            } else {
                player.sendMessage(prefix + ChatColor.YELLOW + targetName + " has no warnings to remove.");
            }
            
            return true;
        }
    }
    
    /**
     * Handles /checkwarn, /checkban, and /checkmute commands.
     */
    private class CheckCommandExecutor implements CommandExecutor {
        private final String commandName;

        public CheckCommandExecutor(String commandName) {
            this.commandName = commandName;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }
            
            Player player = (Player) sender;
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.helper")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            
            if (args.length < 1) {
                player.sendMessage(prefix + ChatColor.RED + "Usage: /" + label + " <player>");
                return false;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target == null || target.getName() == null) {
                player.sendMessage(prefix + ChatColor.RED + "Player '" + targetName + "' not found.");
                return true;
            }
            
            PlayerData targetData = playersData.get(target.getUniqueId());
            if (targetData == null) {
                player.sendMessage(prefix + ChatColor.YELLOW + "No data found for player " + targetName + ".");
                return true;
            }
            
            switch (commandName.toLowerCase()) {
                case "warn":
                    player.sendMessage(prefix + ChatColor.YELLOW + "Warnings for " + targetName + ": " + ChatColor.WHITE + targetData.warns);
                    if (targetData.warns > 0) {
                        player.sendMessage(ChatColor.YELLOW + "Last warned on: " + ChatColor.WHITE + targetData.startWarns);
                    }
                    break;
                case "ban":
                    BanEntry banEntry = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntry(target.getName());
                    if (banEntry != null) {
                        player.sendMessage(prefix + ChatColor.RED + targetName + " is banned.");
                        player.sendMessage(ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + banEntry.getReason());
                        player.sendMessage(ChatColor.YELLOW + "Source: " + ChatColor.WHITE + banEntry.getSource());
                        if (banEntry.getExpiration() != null) {
                            player.sendMessage(ChatColor.YELLOW + "Expires: " + ChatColor.WHITE + banEntry.getExpiration());
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "Expires: " + ChatColor.WHITE + "Never (permanent)");
                        }
                    } else {
                        player.sendMessage(prefix + ChatColor.GREEN + targetName + " is not banned.");
                    }
                    break;
                case "mute":
                    if (targetData.isMuted()) {
                        player.sendMessage(prefix + ChatColor.RED + targetName + " is currently muted.");
                        player.sendMessage(ChatColor.YELLOW + "Mute started at: " + ChatColor.WHITE + targetData.startMutes);
                        if (targetData.mutes > 0) {
                             player.sendMessage(ChatColor.YELLOW + "Mute duration: " + ChatColor.WHITE + targetData.mutes + " minutes");
                        }
                    } else {
                        player.sendMessage(prefix + ChatColor.GREEN + targetName + " is not muted.");
                    }
                    break;
            }
            return true;
        }
    }
    
    /**
     * Handles the /setlogin command, which sets the login location to the player's current location.
     */
    private class SetLoginCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            // Check for a specific permission, e.g., utility.admin
            if (!isLoggedIn(player.getUniqueId()) || !player.hasPermission("utility.setlogin")) {
                player.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            
            Location location = player.getLocation();
            
            // Save the location details to the config file
            getConfig().set("login-spawn.world", location.getWorld().getName());
            getConfig().set("login-spawn.x", location.getX());
            getConfig().set("login-spawn.y", location.getY());
            getConfig().set("login-spawn.z", location.getZ());
            getConfig().set("login-spawn.yaw", location.getYaw());
            getConfig().set("login-spawn.pitch", location.getPitch());
            
            saveConfig();
            
            player.sendMessage(prefix + ChatColor.GREEN + "Login spawn location has been set to your current position.");
            player.sendMessage(prefix + ChatColor.GRAY + "World: " + location.getWorld().getName());
            player.sendMessage(prefix + ChatColor.GRAY + "Coordinates: x=" + (int)location.getX() + ", y=" + (int)location.getY() + ", z=" + (int)location.getZ());
            
            return true;
        }
    }
}
