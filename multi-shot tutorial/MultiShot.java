package Hiro3;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.util.DamageHandler;

public class MultiShot extends AirAbility implements AddonAbility {

	//Listener of your ability. Listener doesn't have anything special for multi-shot ability.
	private Listener MSL;
	
	//Cooldown of your ability.
	private long cooldown;
	//Shot number of your ability.
	private int charge;
	//Range of your projectiles.
	private int range;
	//Damage of your projectiles.
	private double damage;
	//Speed of your projectiles.
	private double speed = 1;
	//Maximum waiting time for players to shoot all of the charges.
	private long duration;
	//Delay between shots to prevent spamming.
	private long timeBetweenShots;
	//A temporary variable for keeping track of last shot time. It is required to put delay between shots.
	private long lastShotTime;
	//Holding last projectile's unique id. Later, we will use this in hasmap as a key.
	private int lastProjectileId;
	
	//Each shot has different location so we use a hashmap to keep track of them. Key of this hashmap is the id of projectile.
	private HashMap<Integer, Location> locations;
	//Each shot has different directions so we use a hashmap to keep track of them. Key of this hashmap is the id of projectile.
	private HashMap<Integer, Vector> directions;
	//Each shot has different starting location so we hashmap to keep track of them. Key of this hashmap is the id of projectile.
	private HashMap<Integer, Location> startLocations;
	//This hashmap is required for removing shots when they touch an entity, a block or when they are out of range. Key of this hashmap is the id of projectile.
	private HashMap<Integer, Location> deadProjectiles;
	
	public MultiShot(Player player) {
		super(player);
		
		//Don't continue if your ability is on cooldown.
		if (bPlayer.isOnCooldown(this)) {
			return;
		}
		
		//Don't continue if player can't use this ability.
		if (!bPlayer.canBend(this)) {
			return;
		}
		
		//If player already has your ability active, create a new shot.
		//Otherwise, set the initial variables and start your ability.
		if (hasAbility(player, MultiShot.class)) {
			//We are in this if block. So, your multi-shot ability was active for the player and he/she clicked it again to shoot another projectile.
			//Code below gets the active instance of your ability for the player.
			//From now on, we can use ms variable to get and set variables of that already active instance of your move.
			MultiShot ms = getAbility(player, MultiShot.class);
			
			//If block below checks if ms has any charges left and is it ready to shoot another projectile.
			if (ms.getCharge() == 0 || System.currentTimeMillis() < ms.getLastShotTime() + ms.getTimeBetweenShots()) {
				return;
			}
			
			//If block below checks if we are shooting the last projectile or not. (Charge is the number of projectiles you can shoot.)
			//If we are shooting the last projectile, it adds cooldown to the player.
			if (ms.getCharge() == 1) {
				bPlayer.addCooldown(ms);
			}
			
			//Determine the id of your new projectile. (It is the last id + 1)
			int projectileId = ms.getLastProjectileId() + 1;
			//Create the starting location of your projectile.
			Location loc = player.getLocation().add(0, 1, 0);
			//Add that location to ms's hashmap of locations.
			//Adding new location to ms means you are adding new projectile to your move.
			ms.getParticleLocations().put(projectileId, loc);
			//Set direction of this new projectile and add it to ms's directions hashmap.
			ms.getDirections().put(projectileId, player.getLocation().getDirection());
			//Set starting location of this new projectile and add it to ms's direction hashmap.
			ms.getStartLocations().put(projectileId, loc.clone());
			//Set last shot time of ms to current time because we just shot a new projectile.
			ms.setLastShotTime(System.currentTimeMillis());
			//Decrease the number of projectiles you can shoot by 1.
			ms.setCharge(ms.getCharge() - 1);
			//Update last projectile id to this projectile's id.
			ms.setLastProjectileId(projectileId);
			
			//Notice that even tho your ability is triggered by clicking, we are not starting a new move.
			//Instead, we are using the ability that is already active and update it's variables to make it
			//manage all the projectiles.
		} else {
			//We are here if the abilty is clicked for the first time.
			//So we will shoot the first projectile.
			//We initialize our variables.
			setField();
			//We start the ability.
			start();
		}
		
	}
	
	public void setField() {
		//Cooldown of your ability.
		cooldown = 2000;
		//Maximum projectile number of your ability.
		charge = 3;
		
		//Range of every projectile.
		range = 20;
		//Damage of every projectile.
		damage = 2;
		
		//If you want to make the range and damage different for each shot,
		//You need to keep track of range and damage using a hashmap similar to what we
		//did for different directions and starting locations.
		
		//Time limit to shoot all of the projectiles.
		duration = 3000;
		//Delay between shooting projectiles.
		timeBetweenShots = 500;
		//Shooting time of last projectile is startTime because we just shot our first projectile.
		lastShotTime = getStartTime();
		
		//Initialize the hashmaps.
		deadProjectiles = new HashMap<Integer, Location>();
		locations = new HashMap<Integer, Location>();
		directions = new HashMap<Integer, Vector>();
		startLocations = new HashMap<Integer, Location>();
		
		//Creating an id for our first projectile.
		lastProjectileId = 1;
		//Create the starting location of first projectile.
		Location loc = player.getLocation().add(0, 1, 0);
		//Add it to hashmap of locations. This hashmap holds each projectile's current location.
		locations.put(lastProjectileId, loc);
		//Add first projectile's direction to hashmap of directions.
		//This hashmap holds each projectile's direction.
		directions.put(lastProjectileId, player.getLocation().getDirection());
		//Add first projectile's starting location to hashmap of starting locations.
		//This hashmap holds each projectile's starting location.
		//We are using starting locations when we check for the range.
		startLocations.put(lastProjectileId, loc.clone());
		
		//Decrease the charge by 1 because we just shot our first projectile.
		charge--;
	}

	@Override
	public void progress() {
		
		//If no charge left (So you cannot shoot more projectiles,
		//and location hashmap is empty (So all of the projectiles are dead)
		//we can remove the move. (We won't add cooldown because we added it
		//when we shot the last projectile.)
		if (charge == 0 && locations.isEmpty()) {
			remove();
			return;
		}
		
		//If duration is over, add cooldown and remove the ability.
		if (System.currentTimeMillis() > getStartTime() + duration) {
			bPlayer.addCooldown(this);
			remove();
		}
		
		//This for loop below is the thing that progress every projectile.
		//i is the projectile's id.
		
		//What this for does is, for each projectile:
		//spawn the particle,
		//check for living entities around to damage one of them.
		//move the projectile to it's next position.
		//check for the range,
		//check if the projectile hits a block.
		for (Integer i : locations.keySet()) {
			//Spawn your i'th projectile's particle at it's location
			player.getWorld().spawnParticle(Particle.SPELL, locations.get(i), 0);
			
			//Check living entities near i'th projectile to damage one of them.
			for (Entity e : GeneralMethods.getEntitiesAroundPoint(locations.get(i), 1.5)) {
				if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
					DamageHandler.damageEntity(e, damage, this);
					//After you damage an entity, you need to remove that projectile unless
					//you want it to go through entities.
					//If we remove it right here, that will break our for loop because we are using
					//locations.size() and removing an element will change it's size.
					//So we use a temporary hashmap to keep track of projectile we want to remove 
					//after for loop is over.
					deadProjectiles.put(i, locations.get(i));
				}
			}
			
			//Move i'th projectile to it's next position.
			locations.get(i).add(directions.get(i).clone().multiply(speed));
			//If it is out of range or it hit a block, add it to the temporary hashmap to remove later.
			if (locations.get(i).distance(startLocations.get(i)) > range
					|| GeneralMethods.isSolid(locations.get(i).getBlock())) {
				deadProjectiles.put(i, locations.get(i));
			}
		}
		
		//Our loop that progress all of the projectiles is over.
		//Now we can safely remove the dead projectiles from every
		//hashmap we used that projectile in.
		for(Integer i : deadProjectiles.keySet()) {
			locations.remove(i);
			directions.remove(i);
			startLocations.remove(i);
		}
		//Finally, we clear our temporary hashmap.
		deadProjectiles.clear();
		
	}
	
	//We use this method to get this instance of your ability's charge value.
	public int getCharge() {
		return this.charge;
	}
	
	//We use this method to update this instance of your ability's charge value.
	public void setCharge(int charge) {
		this.charge = charge;
	}
	
	public int getLastProjectileId() {
		return this.lastProjectileId;
	}
	
	public void setLastProjectileId(int id) {
		this.lastProjectileId = id;
	}
	
	//We use this method to get this instance of your ability's lastShotTime value.
	public long getLastShotTime() {
		return this.lastShotTime;
	}
	
	//We use this method to update this instance of your ability's lastShotTime value.
	public void setLastShotTime(long time) {
		this.lastShotTime = time;
	}
	
	//We use this method to get this instance of your ability's timeBetweenShots value.
	public long getTimeBetweenShots() {
		return this.timeBetweenShots;
	}
	
	//We use this method to get this instance of your ability's locations hashmap.
	public HashMap<Integer, Location> getParticleLocations() {
		return this.locations;
	}
	
	//We use this method to get this instance of your ability's directions hashmap.
	public HashMap<Integer, Vector> getDirections() {
		return this.directions;
	}
	
	//We use this method to get this instance of your ability's startLocations hashmap.
	public HashMap<Integer, Location> getStartLocations() {
		return this.startLocations;
	}
	
	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public String getName() {
		return "MultiShot";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
	}

	@Override
	public String getAuthor() {
		return "Hiro3";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public void load() {
		//We are registering our listener.
		MSL = new MultiShotListener();
		ProjectKorra.plugin.getServer().getPluginManager().registerEvents(MSL, ProjectKorra.plugin);
		ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
	}

	@Override
	public void stop() {
		ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
		//We are unregistering our listener.
		HandlerList.unregisterAll(MSL);
		super.remove();
	}	
	
}
