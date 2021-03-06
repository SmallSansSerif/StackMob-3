package uk.antiperson.stackmob;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import uk.antiperson.stackmob.cache.StorageManager;
import uk.antiperson.stackmob.compat.HookManager;
import uk.antiperson.stackmob.compat.PluginCompat;
import uk.antiperson.stackmob.entity.EntityTools;
import uk.antiperson.stackmob.checks.TraitManager;
import uk.antiperson.stackmob.entity.StackLogic;
import uk.antiperson.stackmob.entity.death.DeathManager;
import uk.antiperson.stackmob.entity.multiplication.DropTools;
import uk.antiperson.stackmob.entity.multiplication.ExperienceTools;
import uk.antiperson.stackmob.listeners.ServerLoad;
import uk.antiperson.stackmob.listeners.chunk.LoadEvent;
import uk.antiperson.stackmob.listeners.chunk.UnloadEvent;
import uk.antiperson.stackmob.listeners.entity.*;
import uk.antiperson.stackmob.listeners.player.ChatEvent;
import uk.antiperson.stackmob.listeners.player.QuitEvent;
import uk.antiperson.stackmob.listeners.player.StickInteractEvent;
import uk.antiperson.stackmob.stick.StickTools;
import uk.antiperson.stackmob.tasks.CacheTask;
import uk.antiperson.stackmob.tasks.ShowTagTask;
import uk.antiperson.stackmob.tasks.TagTask;
import uk.antiperson.stackmob.tools.*;
import uk.antiperson.stackmob.config.ConfigFile;
import uk.antiperson.stackmob.config.EntityLangFile;
import uk.antiperson.stackmob.config.GeneralLangFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Created by nathat on 23/07/17.
 */
public class StackMob extends JavaPlugin {

    private int versionId;
    private ConfigFile config;
    private EntityLangFile entityLang;
    private GeneralLangFile generalLang;
    private EntityTools entityTools;
    private DropTools dropTools;
    private StickTools stickTools;
    private ExperienceTools expTools;
    private StackLogic logic;
    private StorageManager storageManager;
    private HookManager hookManager;
    private TraitManager traitManager;
    private DeathManager deathManager;
    private UpdateChecker updater;

    @Override
    public void onLoad(){
        hookManager = new HookManager(this);
        getHookManager().onServerLoad();
    }

    @Override
    public void onEnable(){
        // Startup messages
        getLogger().info("StackMob v" + getDescription().getVersion() + " created by antiPerson (pas_francais)");
        getLogger().info("Documentation can be found at " + getDescription().getWebsite());
        getLogger().info("GitHub repository can be found at " + GlobalValues.GITHUB);

        // Set version id, but if not supported, warn.
        setVersionId();
        if(getVersionId() == 0){
            getLogger().warning("A bukkit version that is not supported has been detected! (" + Bukkit.getBukkitVersion() + ")");
            getLogger().warning("The features of this version are not supported, so the plugin will not enable!");
            return;
        }
        getLogger().info("Detected server version: " + getVersionId());
        initVariables();

        // Loads configuration file into memory, and if not found, file is copied from the jar file.
        getConfigFile().reloadCustomConfig();
        getTranslationFile().reloadCustomConfig();
        getGeneralFile().reloadCustomConfig();

        // Initialize support for other plugins.
        getHookManager().registerHooks();
        // Register traits for entity comparison.
        getTraitManager().registerTraits();

        if(getCustomConfig().isBoolean("plugin.loginupdatechecker")){
            getLogger().info("An old version of the configuration file has been detected!");
            getLogger().info("A new one will be generated and the old one will be renamed to config.old");
            getConfigFile().generateNewVersion();
        }

        // Load the storage.
        getLogger().info("Loading cached entities...");
        getStorageManager().onServerEnable();

        // Essential listeners/tasks that are needed for the plugin to function correctly.
        getLogger().info("Registering listeners, tasks and commands...");
        registerEssentialEvents();

        // Events that are not required for the plugin to function, however they make a better experience.
        registerNotEssentialEvents();

        getLogger().info("Starting metrics (if enabled)...");
        new MetricsLite(this);

        getLogger().info(getUpdater().updateString());

        if(LocalDate.now().getDayOfYear() == 359){
            getLogger().info("If you are reading this, have a merry christmas!");
        }else if(LocalDate.now().getDayOfYear() == 1){
            getLogger().info("Happy new year!");
        }
    }


    @Override
    public void onDisable(){
        getLogger().info("Cancelling currently running tasks...");
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Saving entity amount storage...");
        // Save the storage so entity amounts aren't lost on restarts
        getStorageManager().onServerDisable();
    }

    // Server version detection, if version isn't currently supported, then versionId is 0.
    private void setVersionId(){
        if(Bukkit.getVersion().contains("1.13")){
            versionId = 1;
        }
    }

    public int getVersionId(){
        return versionId;
    }

    private void initVariables(){
        config = new ConfigFile(this);
        entityLang = new EntityLangFile(this);
        generalLang = new GeneralLangFile(this);
        entityTools = new EntityTools(this);
        dropTools = new DropTools(this);
        stickTools = new StickTools(this);
        expTools = new ExperienceTools(this);
        logic = new StackLogic(this);
        storageManager = new StorageManager(this);
        traitManager = new TraitManager(this);
        deathManager = new DeathManager(this);
        updater = new UpdateChecker(this);
    }

    private void registerEssentialEvents(){
        getServer().getPluginManager().registerEvents(new SpawnEvent(this), this);
        getServer().getPluginManager().registerEvents(new DeathEvent(this), this);
        getServer().getPluginManager().registerEvents(new LoadEvent(this), this);
        getServer().getPluginManager().registerEvents(new UnloadEvent(this), this);
        getServer().getPluginManager().registerEvents(new ServerLoad(this), this);
        getCommand("stackmob").setExecutor(new Commands(this));
        startTasks();
    }

    private void registerNotEssentialEvents(){
        if(getCustomConfig().getBoolean("multiply.creeper-explosion")){
            getServer().getPluginManager().registerEvents(new ExplodeEvent(), this);
        }
        if(getCustomConfig().getBoolean("multiply.chicken-eggs")){
            getServer().getPluginManager().registerEvents(new ItemDrop(this), this);
        }
        if(getCustomConfig().getBoolean("divide-on.sheep-dye")) {
            getServer().getPluginManager().registerEvents(new DyeEvent(this), this);
        }
        if(getCustomConfig().getBoolean("divide-on.breed")){
            getServer().getPluginManager().registerEvents(new InteractEvent(this), this);
        }
        if(getCustomConfig().getBoolean("multiply.small-slimes")) {
            getServer().getPluginManager().registerEvents(new SlimeEvent(), this);
        }
        if(getCustomConfig().getBoolean("multiply-damage-done")){
            getServer().getPluginManager().registerEvents(new DealtDamageEvent(), this);
        }
        if(getCustomConfig().getBoolean("multiply-damage-received.enabled")){
            getServer().getPluginManager().registerEvents(new ReceivedDamageEvent(this), this);
        }
        if(getCustomConfig().getBoolean("no-targeting.enabled")){
            getServer().getPluginManager().registerEvents(new TargetEvent(this), this);
        }
        if(getCustomConfig().getBoolean("divide-on.tame")){
            getServer().getPluginManager().registerEvents(new TameEvent(this), this);
        }
        getServer().getPluginManager().registerEvents(new ShearEvent(this), this);
        getServer().getPluginManager().registerEvents(new BreedEvent(this), this);
        getServer().getPluginManager().registerEvents(new StickInteractEvent(this), this);
        getServer().getPluginManager().registerEvents(new ChatEvent(this), this);
        getServer().getPluginManager().registerEvents(new QuitEvent(this), this);
        getServer().getPluginManager().registerEvents(new ConvertEvent(), this);
    }

    private void startTasks(){
        new TagTask(this).runTaskTimer(this, 0, getCustomConfig().getInt("tag.interval"));
        if(getHookManager().isHookRegistered(PluginCompat.PROCOTOLLIB)){
            new ShowTagTask(this).runTaskTimer(this, 5, getCustomConfig().getInt("tag.interval"));
        }
        if(getCustomConfig().getInt("storage.delay") > 0) {
            new CacheTask(this).runTaskTimer(this, 0, getCustomConfig().getInt("storage.delay") * 20);
        }
    }

    public FileConfiguration getCustomConfig(){
        return config.getCustomConfig();
    }

    public ConfigFile getConfigFile(){
        return config;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public TraitManager getTraitManager() {
        return traitManager;
    }

    public DeathManager getDeathManager() {
        return deathManager;
    }

    public EntityTools getTools() {
        return entityTools;
    }

    public DropTools getDropTools() {
        return dropTools;
    }

    public StickTools getStickTools() {
        return stickTools;
    }

    public FileConfiguration getTranslationConfig() {
        return entityLang.getCustomConfig();
    }

    public EntityLangFile getTranslationFile() {
        return entityLang;
    }

    public ExperienceTools getExpTools() {
        return expTools;
    }

    public FileConfiguration getGeneralConfig() {
        return generalLang.getCustomConfig();
    }

    public GeneralLangFile getGeneralFile() {
        return generalLang;
    }

    public UpdateChecker getUpdater() {
        return updater;
    }

    public StackLogic getLogic() {
        return logic;
    }

    public Map<UUID, Integer> getCache(){
        return getStorageManager().getAmountCache();
    }
}
