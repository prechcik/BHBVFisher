package bh;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.filter.Filter;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.InventoryListener;
import org.dreambot.api.utilities.InventoryMonitor;
import org.dreambot.api.utilities.impl.Condition;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.Item;

@ScriptManifest(author = "Prech", name = "BH - Barbarian Village Fishing", version = 1.0, description = "", category = Category.FISHING)
public class Main extends AbstractScript {

	private Area fishingarea = new Area(3103,3424,3109,3434);
	private int minfishtobank = 20;
	private BankLocation bank = BankLocation.VARROCK_WEST;
	private Area bankarea = bank.getArea(5);
	private int fishcaught = 0;
	private int fishcooked = 0;
	private String bgurl = "https://i.imgur.com/8mzFNMf.jpg";
	private final Image bg = getImage(bgurl);
	
	private Area firearea = new Tile(3105,3432).getArea(3);
	
	private enum Status {
		IDLE, FISHING, DROPPING, BANKING, COOKING
	}
	
	private Status currentStatus;
	
	
	
	@Override
	public int onLoop() {
		
		currentStatus = getStatus();
		switch (currentStatus) {
		case FISHING:
			if (fishingarea.contains(getLocalPlayer())) {
				
				NPC fishingspot = getNpcs().closest("Rod fishing spot");
				if (fishingspot != null && fishingspot.hasAction("Lure")) {
					if (!getLocalPlayer().isAnimating()) {
						fishingspot.interact("Lure");
					}
				}
			} else {
				if (getWalking().getDestinationDistance() < 4) {
					getWalking().walk(fishingarea.getRandomTile());
				}
			}
			break;
		case DROPPING:
				getInventory().dropAll(new Filter<Item>() {

					@Override
					public boolean match(Item arg0) {
						if (arg0.getName().toLowerCase().contains("burnt")) return true;
						return false;
					}
					
				});
			break;
		case BANKING:
			if (bankarea.contains(getLocalPlayer())) {
				if (getBank().isOpen()) {
					boolean deposited = getBank().depositAllExcept("Fly fishing rod","Feather");
					if (deposited == true) {
						getBank().close();
					}
				} else {
					getBank().open();
				}
			} else {
				if (getWalking().getDestinationDistance() < 4) {
					getWalking().walk(bankarea.getRandomTile());
				}
			}
			break;
		case COOKING:
			if (firearea.contains(getLocalPlayer())) {
			GameObject fire = getGameObjects().closest("Fire");
			if (fire != null) {
				if (!getLocalPlayer().isAnimating()) {
					Item fish = getInventory().get("Raw trout", "Raw salmon");
					if (fish != null) {
						if (!getLocalPlayer().isMoving())
						if (fish.useOn(fire)) {
							sleepUntil(new Condition() {

								@Override
								public boolean verify() {
									return !(getWidgets().getWidgetChild(270,14) == null);
								}
								
							},10000);
							getWidgets().getWidgetChild(270,14).interact("Cook");
						}
						
					}
				}
			} else {
				log("No fire around");
			}
			} else {
				getWalking().walk(firearea.getRandomTile());
			}
			break;
		case IDLE:
			
			break;
		}
		moveMouse();
		rotateScreen();
		inspectRandom();
		int nextsleep = Calculations.random(500, 5000);
		log("Sleeping for " + nextsleep + " ms");
		return nextsleep;
	}
	
	
	private Status getStatus() {
		
		
		if (getInventory().contains("Fly fishing rod") && getInventory().count("Feather") > 0) {
				if (getInventory().getEmptySlots() == 0 && (getInventory().contains("Raw trout") || getInventory().contains("Raw salmon"))) {
					return Status.COOKING;
				} else {
					if (getInventory().count("Burnt fish") > 0) {
						return Status.DROPPING;
					} else {
						if ((getInventory().count("Trout") + getInventory().count("Salmon")) >= minfishtobank) {
							return Status.BANKING;
						} else {
							return Status.FISHING;
						}
					}
					
				}
				
		} else {
			return Status.BANKING;
		}
		
	}
	
	@Override
	public void onPaint(Graphics g) {
		g.drawImage(bg, 8, 344, null);
		g.setFont(new Font("Constantia", Font.PLAIN, 24)); 
		g.drawString("" + currentStatus.toString(), 82, 395);
		g.drawString("" + getSkills().getRealLevel(Skill.FISHING), 120, 425);
		g.drawString("" + getSkills().getRealLevel(Skill.COOKING), 130, 455);
	}
	
	
	public void moveMouse() {
		boolean val = new Random().nextInt(50)==0;
		if (val) {
			log("1/50 chance - mouse movement");
			getMouse().move(getClient().getViewportTools().getRandomPointOnCanvas());
		}
	}
	
	private void rotateScreen() {
		boolean val = new Random().nextInt(150)==0;
		if (val) {
			log("1/150 chance - screen movement");
			int newrotation = Calculations.random(0,360);
			int newpitch = Calculations.random(300, 383);
			getCamera().keyboardRotateToPitch(newpitch);
			getCamera().keyboardRotateToYaw(newrotation);
		}
	}
	
	
	private void inspectRandom() {
		
		boolean val = new Random().nextInt(100)==0;
		if (val) {
			log("1/100 chance - right click random npc/player");
			List<Player> players = getPlayers().all();
			if (players.size()>0) {
				int randomplayer = Calculations.random(0, players.size()-1);
				Player rplayer = players.get(randomplayer);
				if (rplayer != null && rplayer.distance() < 5) {
					getMouse().click(rplayer, true);
				}
			} else {
				List<NPC> npcs = getNpcs().all();
				if (npcs.size()>0) {
					int randomnpc = Calculations.random(0, npcs.size()-1);
					NPC rnpc = npcs.get(randomnpc);
					if (rnpc != null && rnpc.distance()<5) {
						getMouse().click(rnpc,true);
					}
				}
			}
		}
		sleep(Calculations.random(1000,2000));
	}
	
	private Image getImage(String url)

	 {

	 try

	 {

	 return ImageIO.read(new URL(url));

	 }

	 catch (IOException e) {}

	 return null;

	 }

}
