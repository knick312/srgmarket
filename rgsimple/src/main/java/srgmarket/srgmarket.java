package srgmarket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class srgmarket extends JavaPlugin {
    private static srgmarket instance;
    private WorldGuardPlugin worldGuardPlugin;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        // Crear carpeta y config.yml si no existe
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig(); // Carga la configuración predeterminada

        // Verificar si WorldGuard está habilitado
        if (!setupWorldGuard()) {
            getLogger().severe("WorldGuard no está habilitado o no se encontró.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Verificar si Vault y economía están habilitados
        if (!setupEconomy()) {
            getLogger().severe("Vault o un sistema de economía compatible no está habilitado.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new MarketListener(this, worldGuardPlugin), this);

        getLogger().info("srgmarket habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        getLogger().info("srgmarket deshabilitado.");
    }

    private boolean setupWorldGuard() {
        worldGuardPlugin = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        return worldGuardPlugin != null;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
        return economy != null;
    }

    public static srgmarket getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }
}
