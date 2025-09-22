import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class UtilityPlugin extends JavaPlugin implements Listener {

    // Prefix for all plugin messages in the chat
    private final String prefix = ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "Utility" + ChatColor.GRAY + "]" + ChatColor.RESET + " ";
    
    // Maps to store player data and scheduled tasks
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loginTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerLocations = new ConcurrentHashMap<>();
    
    // Folder to store player data files
    private File playersFolder;

    @Override
    public void onEnable() {
        getLogger().info("UtilityPlugin has been enabled!");
        
        // Register all command executors with the plugin
        getCommand("login").setExecutor(new LoginCommandExecutor());
        getCommand("register").setExecutor(new RegisterCommandExecutor());
        getCommand("setlogin").setExecutor(new SetLoginCommandExecutor());
        getCommand("ban").setExecutor(new BanCommandExecutor());
        getCommand("unban").setExecutor(new UnbanCommandExecutor());
        getCommand("tempban").setExecutor(new TempBanCommandExecutor());
        getCommand("kick").setExecutor(new KickCommandExecutor());
        getCommand("mute").setExecutor(new MuteCommandExecutor());
        getCommand("unmute").setExecutor(new UnMuteCommandExecutor());
        getCommand("warn").setExecutor(new WarnCommandExecutor());
        getCommand("unwarn").setExecutor(new UnwarnCommandExecutor());
        getCommand("checkwarn").setExecutor(new CheckWarnCommandExecutor());
        getCommand("checkban").setExecutor(new CheckBanCommandExecutor());
        getCommand("checkmute").setExecutor(new CheckMuteCommandExecutor());
        
        // Register this class to listen for events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Save the default configuration file if it doesn't exist
        saveDefaultConfig();
        
        // Load all player data from the files
        loadAllData();
        
        // Create the players data folder if it doesn't exist
        playersFolder = new File(getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("UtilityPlugin has been disabled!");
        // Save all player data before the plugin shuts down
        saveAllData();
    }

    // Loads all player data from the 'players' folder
    private void loadAllData() {
        playersFolder = new File(getDataFolder(), "players");
        if (!playersFolder.exists()) {
            return;
        }

        File[] playerFiles = playersFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (playerFiles != null) {
            for (File file : playerFiles) {
                YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
                UUID playerUUID = UUID.fromString(file.getName().replace(".yml", ""));
                String username = data.getString("Username");
                String passwordHash = data.getString("Password");
                long warns = data.getLong("Warns", 0);
                long startWarns = data.getLong("StartWarns", 0);
                long mutes = data.getLong("Mutes", 0);
                long startMutes = data.getLong("StartMutes", 0);
                long bans = data.getLong("Bans", 0);
                long startBans = data.getLong("StartBans", 0);
                long loginAttempts = data.getLong("loginAttempts", 0);

                playerDataMap.put(playerUUID, new PlayerData(username, passwordHash, warns, startWarns, mutes, startMutes, bans, startBans, loginAttempts));
            }
        }
        getLogger().info("Successfully loaded data for " + playerDataMap.size() + " players.");
    }

    // Saves all player data to the 'players' folder
    private void saveAllData() {
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            UUID playerUUID = entry.getKey();
            PlayerData data = entry.getValue();

            File playerFile = new File(playersFolder, playerUUID.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("Username", data.getUsername());
            config.set("Password", data.getPasswordHash());
            config.set("Warns", data.getWarns());
            config.set("StartWarns", data.getStartWarns());
            config.set("Mutes", data.getMutes());
            config.set("StartMutes", data.getStartMutes());
            config.set("Bans", data.getBans());
            config.set("StartBans", data.getStartBans());
            config.set("loginAttempts", data.getLoginAttempts());

            try {
                config.save(playerFile);
            } catch (IOException e) {
                getLogger().severe("Could not save data for player " + data.getUsername() + ": " + e.getMessage());
            }
        }
        getLogger().info("Successfully saved data for " + playerDataMap.size() + " players.");
    }
    
    // Saves data for a single player to their YML file
    private void savePlayerData(UUID playerUUID) {
        PlayerData data = playerDataMap.get(playerUUID);
        if (data == null) {
            return;
        }
    
        File playerFile = new File(playersFolder, playerUUID.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("Username", data.getUsername());
        config.set("Password", data.getPasswordHash());
        config.set("Warns", data.getWarns());
        config.set("StartWarns", data.getStartWarns());
        config.set("Mutes", data.getMutes());
        config.set("StartMutes", data.getStartMutes());
        config.set("Bans", data.getBans());
        config.set("StartBans", data.getStartBans());
        config.set("loginAttempts", data.getLoginAttempts());
    
        try {
            config.save(playerFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data for player " + data.getUsername() + ": " + e.getMessage());
        }
    }
    
    // Loads data for a single player from their YML file
    private void loadPlayerData(UUID playerUUID) {
        File playerFile = new File(playersFolder, playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            return;
        }
    
        YamlConfiguration data = YamlConfiguration.loadConfiguration(playerFile);
        String username = data.getString("Username");
        String passwordHash = data.getString("Password");
        long warns = data.getLong("Warns", 0);
        long startWarns = data.getLong("StartWarns", 0);
        long mutes = data.getLong("Mutes", 0);
        long startMutes = data.getLong("StartMutes", 0);
        long bans = data.getLong("Bans", 0);
        long startBans = data.getLong("StartBans", 0);
        long loginAttempts = data.getLong("loginAttempts", 0);
    
        playerDataMap.put(playerUUID, new PlayerData(username, passwordHash, warns, startWarns, mutes, startMutes, bans, startBans, loginAttempts));
    }

    // Hashes a password using SHA-256 for secure storage
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            getLogger().severe("SHA-256 algorithm not found.");
            return null;
        }
    }

    public String getPrefix() {
        return prefix;
    }
    
    public boolean isLoggedIn(UUID playerUUID) {
        return playerDataMap.containsKey(playerUUID) && playerDataMap.get(playerUUID).isLoggedIn();
    }
    
    public void setLoggedIn(UUID playerUUID) {
        if (playerDataMap.containsKey(playerUUID)) {
            playerDataMap.get(playerUUID).setLoggedIn(true);
        }
    }
    
    // Checks if a player is currently muted
    private boolean isMuted(UUID playerUUID) {
        if (playerDataMap.containsKey(playerUUID)) {
            PlayerData data = playerDataMap.get(playerUUID);
            if (data.getMutes() > 0) {
                if (data.getMutes() == -1) {
                    return true;
                }
                long muteDuration = TimeUnit.SECONDS.toMillis(data.getMutes());
                long timePassed = System.currentTimeMillis() - data.getStartMutes();
                return timePassed < muteDuration;
            }
        }
        return false;
    }
    
    // Checks if a player is currently banned
    private boolean isBanned(UUID playerUUID) {
        if (playerDataMap.containsKey(playerUUID)) {
            PlayerData data = playerDataMap.get(playerUUID);
            if (data.getBans() > 0) {
                if (data.getBans() == -1) {
                    return true;
                }
                long banDuration = TimeUnit.MINUTES.toMillis(data.getBans());
                long timePassed = System.currentTimeMillis() - data.getStartBans();
                return timePassed < banDuration;
            }
        }
        return false;
    }
    

    // Player Data Class to store all relevant information
    private static class PlayerData {
        private String username;
        private String passwordHash;
        private long warns;
        private long startWarns;
        private long mutes;
        private long startMutes;
        private long bans;
        private long startBans;
        private long loginAttempts;
        private boolean loggedIn;

        public PlayerData(String username, String passwordHash, long warns, long startWarns, long mutes, long startMutes, long bans, long startBans, long loginAttempts) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.warns = warns;
            this.startWarns = startWarns;
            this.mutes = mutes;
            this.startMutes = startMutes;
            this.bans = bans;
            this.startBans = startBans;
            this.loginAttempts = loginAttempts;
            this.loggedIn = false;
        }

        // Getters and setters for all data fields
        public String getUsername() { return username; }
        public String getPasswordHash() { return passwordHash; }
        public long getWarns() { return warns; }
        public long getStartWarns() { return startWarns; }
        public long getMutes() { return mutes; }
        public long getStartMutes() { return startMutes; }
        public long getBans() { return bans; }
        public long getStartBans() { return startBans; }
        public long getLoginAttempts() { return loginAttempts; }
        public boolean isLoggedIn() { return loggedIn; }

        public void setUsername(String username) { this.username = username; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public void setWarns(long warns) { this.warns = warns; }
        public void setStartWarns(long startWarns) { this.startWarns = startWarns; }
        public void setMutes(long mutes) { this.mutes = mutes; }
        public void setStartMutes(long startMutes) { this.startMutes = startMutes; }
        public void setBans(long bans) { this.bans = bans; }
        public void setStartBans(long startBans) { this.startBans = startBans; }
        public void setLoginAttempts(long loginAttempts) { this.loginAttempts = loginAttempts; }
        public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
    }


    // --- Event Handlers ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Load data for the joining player
        loadPlayerData(playerUUID);

        // Check if the player is banned
        if (isBanned(playerUUID)) {
            PlayerData data = playerDataMap.get(playerUUID);
            long banDuration = data.getBans();
            String reason = "You are currently banned from the server.";
            
            if (banDuration > 0) {
                long timeRemaining = banDuration - TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - data.getStartBans());
                reason += ChatColor.RED + " Your ban expires in " + timeRemaining + " minutes.";
            } else {
                reason += ChatColor.RED + " This is a permanent ban.";
            }
            player.kickPlayer(getPrefix() + reason);
            return;
        }

        // Check if the player is registered
        if (!playerDataMap.containsKey(playerUUID) || playerDataMap.get(playerUUID).getPasswordHash() == null) {
            player.sendMessage(getPrefix() + ChatColor.YELLOW + "Welcome! You are not registered. Please use " + ChatColor.AQUA + "/register <password> <confirm_password>" + ChatColor.YELLOW + " to create an account.");
            player.sendMessage(getPrefix() + ChatColor.YELLOW + "Please register within 5 minutes or you will be kicked.");
            
            // Set up a scheduled task to kick the player if they don't register
            BukkitTask loginTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!isLoggedIn(playerUUID)) {
                    player.kickPlayer(ChatColor.RED + "You were kicked for not registering within the time limit.");
                }
            }, 20L * 60 * 5); // 5 minutes in ticks (20 ticks = 1 second)
            loginTasks.put(playerUUID, loginTask);
        } else {
            // Player is registered but not logged in
            setLoggedIn(playerUUID);
            
            player.sendMessage(getPrefix() + ChatColor.YELLOW + "Welcome back! Please log in using " + ChatColor.AQUA + "/login <password>" + ChatColor.YELLOW + " to continue.");
            
            // Store player's location to teleport them back after login
            if (player.getBedSpawnLocation() != null) {
                playerLocations.put(playerUUID, player.getBedSpawnLocation());
            } else {
                playerLocations.put(playerUUID, player.getLocation());
            }
            player.teleport(getLoginSpawnLocation());
            
            // Set up a scheduled task to kick the player if they don't log in
            BukkitTask loginTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!isLoggedIn(playerUUID)) {
                    player.kickPlayer(ChatColor.RED + "You were kicked for not logging in within the time limit.");
                }
            }, 20L * 60 * 5); // 5 minutes in ticks
            loginTasks.put(playerUUID, loginTask);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        
        // Save player data on quit
        savePlayerData(playerUUID);
        
        // Remove from maps
        playerDataMap.remove(playerUUID);
        loginTasks.remove(playerUUID);
        playerLocations.remove(playerUUID);
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Prevent chat if the player is not logged in or is muted
        if (!isLoggedIn(playerUUID)) {
            event.setCancelled(true);
            player.sendMessage(getPrefix() + ChatColor.RED + "You must be logged in to chat.");
        }
        
        if (isMuted(playerUUID)) {
            event.setCancelled(true);
            player.sendMessage(getPrefix() + ChatColor.RED + "You are currently muted and cannot chat.");
        }
    }


    // --- Command Executors ---

    // Login command
    private class LoginCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();

            if (isLoggedIn(playerUUID)) {
                player.sendMessage(getPrefix() + ChatColor.GREEN + "You are already logged in!");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(getPrefix() + ChatColor.RED + "Usage: /login <password>");
                return false;
            }

            PlayerData data = playerDataMap.get(playerUUID);
            if (data == null) {
                player.sendMessage(getPrefix() + ChatColor.RED + "You are not registered. Please use /register to create an account.");
                return true;
            }

            String enteredPassword = args[0];
            String enteredPasswordHash = hashPassword(enteredPassword);

            if (enteredPasswordHash != null && enteredPasswordHash.equals(data.getPasswordHash())) {
                setLoggedIn(playerUUID);
                data.setLoginAttempts(0);
                savePlayerData(playerUUID);
                player.sendMessage(getPrefix() + ChatColor.GREEN + "You have successfully logged in!");

                // Cancel the scheduled kick task
                if (loginTasks.containsKey(playerUUID)) {
                    loginTasks.get(playerUUID).cancel();
                    loginTasks.remove(playerUUID);
                }

                // Teleport back to original location
                if (playerLocations.containsKey(playerUUID)) {
                    Location originalLocation = playerLocations.get(playerUUID);
                    player.teleport(originalLocation);
                    playerLocations.remove(playerUUID);
                }

            } else {
                data.setLoginAttempts(data.getLoginAttempts() + 1);
                savePlayerData(playerUUID);
                
                int attempts = (int) data.getLoginAttempts();
                if (attempts >= 5) {
                    // Kick player and apply bans for repeated failed attempts
                    player.kickPlayer(ChatColor.RED + "Too many failed login attempts. Please try again later.");
                    if (attempts == 5) {
                        data.setBans(5);
                        data.setStartBans(System.currentTimeMillis());
                    } else if (attempts == 6) {
                        data.setBans(24 * 60);
                        data.setStartBans(System.currentTimeMillis());
                    } else if (attempts > 6) {
                        data.setBans(-1); // Permanent ban
                        data.setStartBans(System.currentTimeMillis());
                    }
                    savePlayerData(playerUUID);
                }
                player.sendMessage(getPrefix() + ChatColor.RED + "Incorrect password. Attempt " + attempts + " of 5.");
            }
            return true;
        }
    }

    // Register command
    private class RegisterCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Only players can use this command.");
                return true;
            }
    
            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();
    
            if (playerDataMap.containsKey(playerUUID) && playerDataMap.get(playerUUID).getPasswordHash() != null) {
                player.sendMessage(getPrefix() + ChatColor.RED + "You are already registered!");
                return true;
            }
    
            if (args.length < 2) {
                player.sendMessage(getPrefix() + ChatColor.RED + "Usage: /register <password> <confirm_password>");
                return false;
            }
    
            String password = args[0];
            String confirmPassword = args[1];
    
            if (!password.equals(confirmPassword)) {
                player.sendMessage(getPrefix() + ChatColor.RED + "Passwords do not match. Please try again.");
                return true;
            }
    
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) {
                player.sendMessage(getPrefix() + ChatColor.RED + "There was an error processing your password. Please try again.");
                return true;
            }
    
            PlayerData newData = new PlayerData(player.getName(), hashedPassword, 0, 0, 0, 0, 0, 0, 0);
            playerDataMap.put(playerUUID, newData);
            savePlayerData(playerUUID);
            setLoggedIn(playerUUID);
    
            player.sendMessage(getPrefix() + ChatColor.GREEN + "Account created and logged in successfully!");
            
            // Cancel the scheduled kick task
            if (loginTasks.containsKey(playerUUID)) {
                loginTasks.get(playerUUID).cancel();
                loginTasks.remove(playerUUID);
            }
    
            // Teleport back to original location
            if (playerLocations.containsKey(playerUUID)) {
                Location originalLocation = playerLocations.get(playerUUID);
                player.teleport(originalLocation);
                playerLocations.remove(playerUUID);
            }
            return true;
        }
    }

    // Ban command
    private class BanCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /ban <player> <reason>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);
            if (targetData == null) {
                targetData = new PlayerData(target.getName(), null, 0, 0, 0, 0, 0, 0, 0);
                playerDataMap.put(targetUUID, targetData);
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            targetData.setBans(-1);
            targetData.setStartBans(System.currentTimeMillis());
            savePlayerData(targetUUID);

            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason, null, sender.getName());
            target.kickPlayer(getPrefix() + ChatColor.RED + "You have been permanently banned from the server for: " + ChatColor.YELLOW + reason);
            Bukkit.broadcastMessage(getPrefix() + ChatColor.RED + target.getName() + " has been permanently banned by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            
            return true;
        }
    }

    // Unban command
    private class UnbanCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /unban <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            UUID targetUUID;
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }
            targetUUID = target.getUniqueId();

            if (!playerDataMap.containsKey(targetUUID) || playerDataMap.get(targetUUID).getBans() == 0) {
                sender.sendMessage(getPrefix() + ChatColor.RED + args[0] + " is not currently banned by this plugin.");
                return true;
            }

            playerDataMap.get(targetUUID).setBans(0);
            playerDataMap.get(targetUUID).setStartBans(0);
            savePlayerData(targetUUID);

            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(args[0]);
            sender.sendMessage(getPrefix() + ChatColor.GREEN + args[0] + " has been unbanned.");
            return true;
        }
    }
    
    // Temporary ban command
    private class TempBanCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 3) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /tempban <player> <duration_minutes> <reason>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);
            if (targetData == null) {
                targetData = new PlayerData(target.getName(), null, 0, 0, 0, 0, 0, 0, 0);
                playerDataMap.put(targetUUID, targetData);
            }

            long duration;
            try {
                duration = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Invalid duration. Please enter a number in minutes.");
                return false;
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            targetData.setBans(duration);
            targetData.setStartBans(System.currentTimeMillis());
            savePlayerData(targetUUID);

            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason, new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(duration)), sender.getName());
            target.kickPlayer(getPrefix() + ChatColor.RED + "You have been temporarily banned from the server for " + duration + " minutes for: " + ChatColor.YELLOW + reason);
            Bukkit.broadcastMessage(getPrefix() + ChatColor.RED + target.getName() + " has been temporarily banned by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            
            return true;
        }
    }
    
    // Kick command
    private class KickCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /kick <player> <reason>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            target.kickPlayer(getPrefix() + ChatColor.RED + "You have been kicked from the server for: " + ChatColor.YELLOW + reason);
            Bukkit.broadcastMessage(getPrefix() + ChatColor.RED + target.getName() + " has been kicked by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            
            return true;
        }
    }
    
    // Mute command
    private class MuteCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /mute <player> <reason>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);
            if (targetData == null) {
                targetData = new PlayerData(target.getName(), null, 0, 0, 0, 0, 0, 0, 0);
                playerDataMap.put(targetUUID, targetData);
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            if (isMuted(targetUUID)) {
                sender.sendMessage(getPrefix() + ChatColor.YELLOW + target.getName() + " is already muted.");
                return true;
            }

            targetData.setMutes(-1); // Permanent mute
            targetData.setStartMutes(System.currentTimeMillis());
            savePlayerData(targetUUID);

            target.sendMessage(getPrefix() + ChatColor.RED + "You have been muted by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " has been permanently muted.");
            Bukkit.broadcastMessage(getPrefix() + ChatColor.RED + target.getName() + " has been permanently muted by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            
            return true;
        }
    }

    // Unmute command
    private class UnMuteCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /unmute <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            
            if (!playerDataMap.containsKey(targetUUID) || playerDataMap.get(targetUUID).getMutes() == 0) {
                sender.sendMessage(getPrefix() + ChatColor.YELLOW + target.getName() + " is not currently muted.");
                return true;
            }

            playerDataMap.get(targetUUID).setMutes(0);
            playerDataMap.get(targetUUID).setStartMutes(0);
            savePlayerData(targetUUID);

            target.sendMessage(getPrefix() + ChatColor.GREEN + "You have been unmuted by " + sender.getName() + ".");
            sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " has been unmuted.");
            
            return true;
        }
    }
    
    // Warn command
    private class WarnCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /warn <player> <reason>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);
            if (targetData == null) {
                targetData = new PlayerData(target.getName(), null, 0, 0, 0, 0, 0, 0, 0);
                playerDataMap.put(targetUUID, targetData);
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            targetData.setWarns(targetData.getWarns() + 1);
            targetData.setStartWarns(System.currentTimeMillis());
            savePlayerData(targetUUID);

            target.sendMessage(getPrefix() + ChatColor.RED + "You have been warned by " + sender.getName() + " for: " + ChatColor.YELLOW + reason);
            sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " has been warned. They now have " + targetData.getWarns() + " warnings.");
            
            return true;
        }
    }
    
    // Unwarn command
    private class UnwarnCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /unwarn <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);

            if (targetData == null || targetData.getWarns() == 0) {
                sender.sendMessage(getPrefix() + ChatColor.YELLOW + target.getName() + " has no warnings to remove.");
                return true;
            }

            targetData.setWarns(targetData.getWarns() - 1);
            savePlayerData(targetUUID);

            sender.sendMessage(getPrefix() + ChatColor.GREEN + "Warning removed from " + target.getName() + ". They now have " + targetData.getWarns() + " warnings.");
            target.sendMessage(getPrefix() + ChatColor.GREEN + "A warning has been removed from your record.");
            
            return true;
        }
    }

    // Checkwarn command
    private class CheckWarnCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /checkwarn <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            PlayerData targetData = playerDataMap.get(targetUUID);

            if (targetData == null) {
                sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " has no warnings.");
            } else {
                sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " has " + targetData.getWarns() + " warning(s).");
            }
            return true;
        }
    }

    // Checkban command
    private class CheckBanCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /checkban <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            if (isBanned(targetUUID)) {
                sender.sendMessage(getPrefix() + ChatColor.RED + target.getName() + " is currently banned.");
            } else {
                sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " is not currently banned.");
            }
            return true;
        }
    }

    // Checkmute command
    private class CheckMuteCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Usage: /checkmute <player>");
                return false;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Player not found.");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            if (isMuted(targetUUID)) {
                sender.sendMessage(getPrefix() + ChatColor.RED + target.getName() + " is currently muted.");
            } else {
                sender.sendMessage(getPrefix() + ChatColor.GREEN + target.getName() + " is not currently muted.");
            }
            return true;
        }
    }

    // Setlogin command
    private class SetLoginCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getPrefix() + ChatColor.RED + "Only players can use this command.");
                return true;
            }
            
            Player player = (Player) sender;
            getConfig().set("login_spawn", player.getLocation());
            saveConfig();
            player.sendMessage(getPrefix() + ChatColor.GREEN + "Login spawn location has been set to your current location!");
            return true;
        }
    }
    
    // Gets the configured login spawn location
    private Location getLoginSpawnLocation() {
        if (getConfig().contains("login_spawn")) {
            return getConfig().getLocation("login_spawn");
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
