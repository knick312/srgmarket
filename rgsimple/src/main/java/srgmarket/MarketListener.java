package srgmarket;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

public class MarketListener implements Listener {

    private final srgmarket plugin;
    private final WorldGuardPlugin worldGuardPlugin;
    private final Economy economy;
    private final RegionContainer regionContainer;
    private final Map<UUID, List<String>> playerRegions;
    private final Map<String, Long> rentedRegions; // Mapa para guardar el tiempo de alquiler de las regiones

    public MarketListener(srgmarket plugin, WorldGuardPlugin worldGuardPlugin) {
        this.plugin = plugin;
        this.worldGuardPlugin = worldGuardPlugin;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
            this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            this.playerRegions = new HashMap<>();
            this.rentedRegions = new HashMap<>(); // Inicialización del mapa de alquileres
        } else {
            throw new IllegalStateException("No se encontró un proveedor de economía compatible con Vault.");
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String[] lines = event.getLines();

        if (lines[0].equalsIgnoreCase("[Market]")) {
            String regionName = lines[1];
            double price;

            try {
                price = Double.parseDouble(lines[2].replace("$", "").trim());
            } catch (NumberFormatException e) {
                price = this.plugin.getConfig().getDouble("general.default_price", 500.0);
            }

            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(player.getWorld()));
            if (regionManager == null || !regionManager.hasRegion(regionName)) {
                player.sendMessage(ChatColor.RED + "La región '" + regionName + "' no existe en WorldGuard.");
                return;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (!region.getOwners().contains(player.getUniqueId()) && !player.hasPermission("srgmarket.admin")) {
                player.sendMessage(ChatColor.RED + "No eres el propietario de la región '" + regionName + "' para venderla.");
                return;
            }

            event.setLine(0, ChatColor.GREEN + "[ForSale]");
            event.setLine(1, regionName);
            event.setLine(2, ChatColor.YELLOW + "$" + price);
            event.setLine(3, getRegionSize(region));

            player.sendMessage(ChatColor.GREEN + "¡Cartel de venta creado para la región '" + regionName + "'!");
        } else if (lines[0].equalsIgnoreCase("[Rent]")) {
            String regionName = lines[1];
            double rentPrice;

            try {
                rentPrice = Double.parseDouble(lines[2].replace("$", "").trim());
            } catch (NumberFormatException e) {
                rentPrice = this.plugin.getConfig().getDouble("general.default_rent_price", 100.0);
            }

            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(player.getWorld()));
            if (regionManager == null || !regionManager.hasRegion(regionName)) {
                player.sendMessage(ChatColor.RED + "La región '" + regionName + "' no existe en WorldGuard.");
                return;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (!region.getOwners().contains(player.getUniqueId()) && !player.hasPermission("srgmarket.admin")) {
                player.sendMessage(ChatColor.RED + "No eres el propietario de la región '" + regionName + "' para rentarla.");
                return;
            }

            // Definimos una duración predeterminada (por ejemplo, 24 horas)
            long rentDuration = 24 * 60 * 60 * 1000; // 24 horas en milisegundos
            long rentEndTime = System.currentTimeMillis() + rentDuration;

            // Actualizamos el cartel para mostrar la duración en la penúltima línea
            event.setLine(0, ChatColor.BLUE + "[Rent]");
            event.setLine(1, regionName);
            event.setLine(2, ChatColor.YELLOW + "$" + rentPrice);
            event.setLine(3, "Duración: 24h");

            // Guardamos el tiempo de finalización del alquiler en el mapa
            rentedRegions.put(regionName, rentEndTime);

            player.sendMessage(ChatColor.GREEN + "¡Cartel de renta creado para la región '" + regionName + "'!");
        }
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getState() instanceof Sign) {
                Sign sign = (Sign) clickedBlock.getState();
                String[] lines = sign.getLines();

                if (ChatColor.stripColor(lines[0]).equalsIgnoreCase("[ForSale]")) {
                    String regionName = lines[1];
                    double price;

                    try {
                        price = Double.parseDouble(lines[2].replace("$", "").trim());
                    } catch (NumberFormatException e) {
                        price = this.plugin.getConfig().getDouble("general.default_price", 500.0);
                    }

                    buyRegion(event.getPlayer(), regionName, price, sign);
                } else if (ChatColor.stripColor(lines[0]).equalsIgnoreCase("[Rent]")) {
                    String regionName = lines[1];
                    double rentPrice;

                    try {
                        rentPrice = Double.parseDouble(lines[2].replace("$", "").trim());
                    } catch (NumberFormatException e) {
                        rentPrice = this.plugin.getConfig().getDouble("general.default_rent_price", 100.0);
                    }

                    rentRegion(event.getPlayer(), regionName, rentPrice, sign);
                }
            }
        }
    }

    private void buyRegion(Player player, String regionName, double price, Sign sign) {
        RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(player.getWorld()));
        if (regionManager != null && regionManager.hasRegion(regionName)) {
            if (economy.getBalance(player) < price) {
                player.sendMessage(ChatColor.RED + "No tienes suficiente dinero para comprar esta región.");
                return;
            }

            economy.withdrawPlayer(player, price);

            ProtectedRegion region = regionManager.getRegion(regionName);

            // Transferir la propiedad de la región al jugador, solo si no es ya el propietario
            if (!region.getOwners().contains(player.getUniqueId())) {
                region.getOwners().clear();
                region.getOwners().addPlayer(player.getUniqueId());
            }

            // Registrar la región comprada por el jugador
            playerRegions.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(regionName);

            player.sendMessage(ChatColor.YELLOW + "¡Has comprado la región '" + regionName + "' por $" + price + "!");
            sign.setLine(0, ChatColor.RED + "[Sold]");
            sign.setLine(3, player.getName()); // Mostrar el nombre del jugador en la última línea

            // Mostrar las dimensiones de la región en la penúltima línea
            String regionSize = getRegionSize(region);
            sign.setLine(2, regionSize);

            sign.update();

            // Depuración
            player.sendMessage(ChatColor.GREEN + "El cartel ha sido actualizado a 'Vendida'!");
            Bukkit.getLogger().info("Cartel de la región '" + regionName + "' actualizado a 'Vendida' para " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "La región '" + regionName + "' no existe.");
        }
    }

    private void rentRegion(Player player, String regionName, double rentPrice, Sign sign) {
        RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(player.getWorld()));
        if (regionManager != null && regionManager.hasRegion(regionName)) {
            if (economy.getBalance(player) < rentPrice) {
                player.sendMessage(ChatColor.RED + "No tienes suficiente dinero para alquilar esta región.");
                return;
            }

            economy.withdrawPlayer(player, rentPrice);

            ProtectedRegion region = regionManager.getRegion(regionName);

            // Guardar el tiempo de finalización del alquiler
            long rentDuration = 24 * 60 * 60 * 1000; // 24 horas en milisegundos
            long rentEndTime = System.currentTimeMillis() + rentDuration;
            rentedRegions.put(regionName, rentEndTime);

            // Establecer el dueño temporalmente en la región alquilada
            region.getOwners().clear();
            region.getOwners().addPlayer(player.getUniqueId());

            player.sendMessage(ChatColor.YELLOW + "¡Has alquilado la región '" + regionName + "' por $" + rentPrice + "!");
            sign.setLine(0, ChatColor.RED + "[Rented]");
            sign.setLine(3, player.getName()); // Mostrar solo el nombre del jugador

            // Actualizar la penúltima línea con la duración restante
            long remainingTime = getRentTimeLeft(regionName); // Tiempo restante de alquiler
            if (remainingTime > 0) {
                String timeRemaining = formatTime(remainingTime);
                sign.setLine(2, "Restante: " + timeRemaining);
            } else {
                sign.setLine(2, "Alquiler expirado");
            }

            sign.update();

            // Depuración
            player.sendMessage(ChatColor.GREEN + "El cartel ha sido actualizado a 'Alquilada'!");
            Bukkit.getLogger().info("Cartel de la región '" + regionName + "' actualizado a 'Alquilada' para " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "La región '" + regionName + "' no existe.");
        }
    }

    private String getRegionSize(ProtectedRegion region) {
        if (region != null) {
            int width = region.getMaximumPoint().getBlockX() - region.getMinimumPoint().getBlockX();
            int length = region.getMaximumPoint().getBlockZ() - region.getMinimumPoint().getBlockZ();
            int height = region.getMaximumPoint().getBlockY() - region.getMinimumPoint().getBlockY();
            return width + " x " + length + " x " + height;
        }
        return "Desconocido";
    }

    public long getRentTimeLeft(String regionName) {
        Long rentEndTime = rentedRegions.get(regionName);
        if (rentEndTime == null) {
            return 0;
        }

        long timeLeft = rentEndTime - System.currentTimeMillis();
        if (timeLeft <= 0) {
            rentedRegions.remove(regionName); // Eliminar la región si el alquiler ha expirado
        }

        return timeLeft > 0 ? timeLeft : 0;
    }

    private String formatTime(long remainingTime) {
        long hours = remainingTime / 3600000;
        long minutes = (remainingTime % 3600000) / 60000;
        return hours + "h " + minutes + "m";
    }
}
