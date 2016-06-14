package org.inventivetalent.verticalrails;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.inventivetalent.reflection.minecraft.Minecraft;
import org.inventivetalent.reflection.resolver.FieldResolver;
import org.inventivetalent.reflection.resolver.minecraft.NMSClassResolver;
import org.mcstats.MetricsLite;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class VerticalRails extends JavaPlugin implements Listener {

	static NMSClassResolver nmsClassResolver = new NMSClassResolver();

	static Class<?> Entity                 = nmsClassResolver.resolveSilent("Entity");
	static Class<?> EntityMinecartAbstract = nmsClassResolver.resolveSilent("EntityMinecartAbstract");

	static FieldResolver EntityFieldResolver = new FieldResolver(Entity);

	Map<Entity, float[]> passengerSizes = new HashMap<>();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);

		try {
			MetricsLite metrics = new MetricsLite(this);
			if (metrics.start()) {
				getLogger().info("Metrics started");
			}
		} catch (Exception e) {
		}
	}

	@EventHandler
	public void onVehicleMove(VehicleMoveEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			Minecart cart = (Minecart) event.getVehicle();
			Block from = event.getFrom().getBlock();
			Block to = event.getTo().getBlock();

			try {
				Object nmsMinecart = Minecraft.getHandle(cart);

				if (from.getType() == Material.LADDER || to.getType() == Material.LADDER) {
					if (!cart.hasMetadata("on_ladder")) {
						Location loc = cart.getLocation();

						loc.setX(to.getX() + .5);
						loc.setZ(to.getZ() + .5);
						cart.setVelocity(new Vector());
						cart.teleport(loc);// Center the cart in all directions

						if (cart.getPassenger() != null) {
							Object passengerHandle = Minecraft.getHandle(cart.getPassenger());
							float passengerWidth = EntityFieldResolver.resolve("width").getFloat(passengerHandle);
							float passengerLength = EntityFieldResolver.resolve("length").getFloat(passengerHandle);

							if (passengerWidth != 0 && passengerLength != 0) {
								this.passengerSizes.put(cart.getPassenger(), new float[] {
										passengerWidth,
										passengerLength });
							}
							this.setEntitySize(passengerHandle, 0.1f, 0.1f);
						}

						cart.setMetadata("on_ladder", new FixedMetadataValue(this, System.currentTimeMillis()));
					}

					float yaw = 0;
					float pitch = 0;

					switch (from.getData()) {
						case 2:
							yaw = 270;
							pitch = -90;
							break;
						case 3:
							yaw = 270;
							pitch = 90f;
							break;
						case 4:
							yaw = 0;
							pitch = 90f;
							break;
						case 5:
							yaw = 0;
							pitch = -90f;
							break;
						default:
							break;
					}

					this.setEntityPosition(nmsMinecart, cart.getLocation().getX(), cart.getLocation().getY(), cart.getLocation().getZ(), yaw, pitch);
					this.setEntitySize(nmsMinecart, 0.1f, 0.1f);

					cart.setVelocity(cart.getVelocity().add(new Vector(0, 0.1, 0)));
					cart.teleport(cart.getLocation().add(0, 0.1, 0));

					if (to.getType() != Material.LADDER) {
						Vector vel = cart.getVelocity();
						vel.setY(0.25);
						switch (from.getData()) {
							case 2:
								cart.setVelocity(vel.add(new Vector(0, 0, 0.1)));
								break;
							case 3:
								cart.setVelocity(vel.add(new Vector(0, 0, -0.1)));
								break;
							case 4:
								cart.setVelocity(vel.add(new Vector(0.1, 0, 0)));
								break;
							case 5:
								cart.setVelocity(vel.add(new Vector(-0.1, 0, 0)));
								break;

							default:
								break;
						}
					}
				} else {
					if (cart.hasMetadata("on_ladder")) {
						long since = cart.getMetadata("on_ladder").get(0).asLong();
						if (System.currentTimeMillis() - since > 500) {
							cart.removeMetadata("on_ladder", this);
							this.setEntitySize(nmsMinecart, 0.98f, 0.7f);// Spigot really needs a method for this
							if (cart.getPassenger() != null) {
								this.onExit(new VehicleExitEvent(null, (LivingEntity) cart.getPassenger()));
							}
						}
					}
				}
			} catch (ReflectiveOperationException e1) {
				throw new RuntimeException(e1);
			}
		}
	}

	@EventHandler
	public void onExit(VehicleExitEvent event) {
		if (this.passengerSizes.containsKey(event.getExited())) {
			float[] value = this.passengerSizes.get(event.getExited());
			try {
				this.setEntitySize(Minecraft.getHandle(event.getExited()), value[0], value[1]);
			} catch (ReflectiveOperationException e1) {
				throw new RuntimeException(e1);
			}
			this.passengerSizes.remove(event.getExited());
		}
	}

	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity().getVehicle() != null) {
			if (e.getEntity().getVehicle().hasMetadata("on_ladder")) {
				e.setCancelled(true);
			}
		}
	}

	void setEntitySize(Object entity, float f, float f1) {
		try {
			setSize.invoke(entity, f, f1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void setEntityPosition(Object entity, double x, double y, double z, float yaw, float pitch) {
		try {
			setPositionRotation.invoke(entity, x, y, z, yaw, pitch);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static Method setSize;
	static Method setPositionRotation;

	static {
		try {
			setSize = Entity.getDeclaredMethod("setSize", float.class, float.class);
			setPositionRotation = Entity.getDeclaredMethod("setPositionRotation", double.class, double.class, double.class, float.class, float.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
