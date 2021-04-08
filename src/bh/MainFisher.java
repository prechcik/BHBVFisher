package bh;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import org.dreambot.api.Client;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.filter.Filter;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.event.impl.ExperienceEvent;
import org.dreambot.api.script.listener.ExperienceListener;
import org.dreambot.api.utilities.Timer;
import org.dreambot.api.utilities.impl.Condition;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.wrappers.widgets.WidgetChild;

@ScriptManifest(author = "Prech", name = "BH - Barbarian Village Fishing", version = 1.2, description = "", category = Category.FISHING)
public class MainFisher extends AbstractScript implements ExperienceListener {

	private String bgurl = "https://i.imgur.com/8mzFNMf.jpg";
	private final Image bg = getImage(bgurl);
	public FisherGUI gui;
	public boolean running = false;
	public BankLocation[] bankLocations;
	public BankLocation selectedBank;
	public String selectedAction;
	public String selectedBankString;
	public boolean cook,drop,bank;
	public Area trainingArea = new Area(3101, 3435, 3111, 3421);
	public List<String> rawFish = Arrays.asList("Raw trout", "Raw salmon", "Raw rainbow fish", "Raw pike");
	public List<String> cookedFish = Arrays.asList("Trout", "Salmon", "Rainbow fish", "Pike");
	public long currentTimer = System.currentTimeMillis();
	public int afkLength = 15;
	public long idleTimer = 0;
	public int startingFishing, startingCooking;
	public long startTime;
	public long afkTimer;
	public long afkEnd = 0;
	public ZenAntiBan antiban;
	public Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
	public String savedStyle, savedDrop, savedBankLocation;
	public boolean savedCook = false;
	public Timer fishingTimer;
	public Timer cookingTimer;
	public int timeSinceLastCook = 0;
	public int currentFishingExpPerH, currentCookingExpPerH, startingFishingExp, startingCookingExp;
	public java.util.Timer cookT;
	public enum Status {
		IDLE, FISHING, COOKING, BANK, DROPPING, AFK, ANTIBAN
	}
	public Status currentStatus = Status.IDLE;

	@Override
	public void onStart() {
		antiban = new ZenAntiBan(this);
		antiban.setStatsToCheck(Skill.FISHING, Skill.COOKING);
		antiban.MIN_WAIT_NO_ACTION = 350;
		antiban.MAX_WAIT_NO_ACTION = 1000;
		antiban.MIN_WAIT_BETWEEN_EVENTS = 350;
		try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					gui = new FisherGUI();
				}
			});
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		bankLocations = BankLocation.values();
		gui.bankLocation.removeAllItems();
		for(BankLocation b : bankLocations) {
			gui.bankLocation.addItem(b.name());
		}
		
		savedStyle = prefs.get("SavedStyle", "lure");
		savedDrop = prefs.get("SavedDrop", "drop");
		savedBankLocation = prefs.get("Bank", "");
		savedCook = prefs.getBoolean("SavedCook", false);
		
		
		gui.antibanSlider.setValue(prefs.getInt("Antiban", 50));
		gui.startBtn.addActionListener(new ActionListener() { 
			  public void actionPerformed(ActionEvent e) { 
				    selectedBankString = gui.bankLocation.getSelectedItem().toString();
				    selectedBank = BankLocation.valueOf(selectedBankString);
				    cook = gui.cookCheckbox.isSelected();
				    drop = gui.dropRadio.isSelected();
				    bank = gui.bankRadio.isSelected();
				    if (gui.baitCheckBox.isSelected()) {
				    	selectedAction = "Bait";
				    	prefs.put("SavedStyle", "bait");
				    } else {
				    	selectedAction = "Lure";
				    	prefs.put("SavedStyle", "lure");
				    }
				    if (drop && !bank) {
				    	prefs.put("SavedDrop", "drop");
				    } else if (!drop && bank) {
				    	prefs.put("SavedDrop", "bank");
				    }
				    prefs.put("Bank", gui.bankLocation.getSelectedItem().toString());
				    prefs.putBoolean("SavedCook", cook);
				    prefs.putInt("Antiban", gui.antibanSlider.getValue());
				    startTime = System.currentTimeMillis();
				    fishingTimer = new Timer();
				    cookingTimer = new Timer();
				    startingFishing = Skills.getRealLevel(Skill.FISHING);
				    startingCooking = Skills.getRealLevel(Skill.COOKING);
				    startingFishingExp = Skills.getExperience(Skill.FISHING);
				    startingCookingExp = Skills.getExperience(Skill.COOKING);
				    antiban.ANTIBAN_RATE = gui.antibanSlider.getValue();
				    log("Set Antiban threshold to " + antiban.ANTIBAN_RATE);
				    cookT = new java.util.Timer();
				    cookT.scheduleAtFixedRate(new java.util.TimerTask() {

						@Override
						public void run() {
							timeSinceLastCook++;						
						}
				    	
				    }, 0, 1000);
				    log("Selected bank " + selectedBank.name() + ", Action: " + selectedAction + ", Cook: " + cook + ", Drop: " + drop + ", Bank: " + bank);
				    running = true;
				    gui.setVisible(false);
				  } 
				} );
		
		gui.setVisible(true);
		if (savedStyle == "bait") {
			gui.baitCheckBox.doClick();
		} else {
			gui.lureCheckBox.doClick();
		}
		if (savedDrop == "bank") {
			gui.bankRadio.doClick();
		} else {
			gui.dropRadio.doClick();
		}
		if (savedBankLocation != "") {
			gui.bankLocation.setSelectedItem(savedBankLocation);
		}
		if (savedCook) {
			gui.cookCheckbox.setSelected(true);
		}
		
	}
	
	@Override
	public int onLoop() {
		currentTimer = System.currentTimeMillis();
		if (running) currentStatus = getStatus();
		switch(currentStatus) {
		case IDLE:
			
			break;
		case FISHING:
			if (trainingArea.contains(getLocalPlayer())) {
				if (!getLocalPlayer().isAnimating()) {
					NPC fishingSpot = NPCs.closest("Rod Fishing spot");
					if (fishingSpot != null) {
						if (fishingSpot.interact(selectedAction)) {
							sleepUntil(new Condition() {

								@Override
								public boolean verify() {
									return getLocalPlayer().isAnimating();
								}
							
							},5000);
						}
					}
				} else {
					if(antiban.doRandom())
			            log("[AntiBan] Random Event Performed");
					sleep(antiban.antiBan());
				}
			} else {
				if (Walking.getDestinationDistance() < 5) {
					Walking.walk(trainingArea.getRandomTile());
				}
			}
			break;
		case COOKING:
			if (trainingArea.contains(getLocalPlayer())) {
				if (!getLocalPlayer().isAnimating() && timeSinceLastCook > 5) {
					GameObject fire = GameObjects.closest("Fire");
					if (fire != null) {
						Item fishToCook = Inventory.get(new Filter<Item>() {

							@Override
							public boolean match(Item arg0) {
								return rawFish.contains(arg0.getName());
							}
							
						});
						if (fishToCook != null && fishToCook.useOn(fire)) {
							WidgetChild cookWindow;
							sleepUntil(new Condition() {

								@Override
								public boolean verify() {
									
									return Widgets.getWidgetChild(270,13) != null;
								}
								
							},5000);
							cookWindow = Widgets.getWidgetChild(270,13);
							if (cookWindow != null && cookWindow.interact()) {
								sleepUntil(new Condition() {

									@Override
									public boolean verify() {
										if (Dialogues.canContinue()) Dialogues.continueDialogue();
										return getLocalPlayer().isAnimating();
									}
									
								},10000);
								sleepUntil(new Condition() {

									@Override
									public boolean verify() {
										sleep(antiban.antiBan());
										return timeSinceLastCook > 5;
									}
									
								},20000);
							}
						}
					}
				} else {
					if (Dialogues.canContinue()) Dialogues.continueDialogue();
					if(antiban.doRandom())
			            log("[AntiBan] Random Event Performed");
					
				}
			} else {
				if (Walking.getDestinationDistance() < 5) {
					Walking.walk(trainingArea.getRandomTile());
				}
			}
			break;
		case BANK:
			if (selectedBank.getArea(10).contains(getLocalPlayer())) {
				if (Bank.isOpen()) {
					Bank.depositAllExcept(new Filter<Item>() {

						@Override
						public boolean match(Item arg0) {
							return !rawFish.contains(arg0.getName()) && !cookedFish.contains(arg0.getName());
						}
						
					});
					sleep(750,3500);
					Bank.close();
				} else {
					Bank.open();
				}
			} else {
				if (Walking.getDestinationDistance() < 5) {
					Walking.walk(selectedBank.getCenter());
				}
				if(antiban.doRandom())
		            log("[AntiBan] Random Event Performed");
			}
			break;
		case DROPPING:
			
			Inventory.dropAll(new Filter<Item>() {

				@Override
				public boolean match(Item arg0) {
					if (bank && cook) {
						return arg0.getName().equals("Burnt fish");
					} else {
						return rawFish.contains(arg0.getName()) || cookedFish.contains(arg0.getName()) || arg0.getName().equals("Burnt fish");
					} 
				}
					
			});
			break;
		case AFK:
			Mouse.moveMouseOutsideScreen();
			afkLength = Calculations.random(10 * 1000, 60 * 1000);
			log("AFK'ing for " + afkLength / 1000 + " seconds");
			afkEnd = System.currentTimeMillis() + afkLength;
			sleep(afkLength);
			break;
		case ANTIBAN:
			
			break;
		default:
			break;
		}
		
		
		
		return antiban.antiBan();
	}
	
	
	@Override
	public void onPaint(Graphics g) {
		g.drawImage(bg, 8, Client.getViewportHeight() - 160, null);
		g.setFont(new Font("Constantia", Font.PLAIN, 18)); 
		g.drawString("" + currentStatus.toString(), 82, Client.getViewportHeight() - 110);
		currentFishingExpPerH = fishingTimer.getHourlyRate(Skills.getExperience(Skill.FISHING) - startingFishingExp);
		g.drawString("" + Skills.getRealLevel(Skill.FISHING) + " - Gained levels: " + (Skills.getRealLevel(Skill.FISHING) - startingFishing) + " - " + formatNumber(currentFishingExpPerH) + " exp/h", 120, Client.getViewportHeight() - 80);
		currentCookingExpPerH = cookingTimer.getHourlyRate(Skills.getExperience(Skill.COOKING) - startingCookingExp);
		g.drawString("" + Skills.getRealLevel(Skill.COOKING) + " - Gained levels: " + (Skills.getRealLevel(Skill.COOKING) - startingCooking) + " - " + formatNumber(currentCookingExpPerH) + " exp/h", 130, Client.getViewportHeight() - 50);
		if (startTime > 0) {
			g.setFont(new Font("Constantia", Font.PLAIN, 14)); 
			g.drawString("Script time: " + formatSeconds((int)(System.currentTimeMillis() - startTime) / 1000), 375, Client.getViewportHeight() - 35);
		}
		g.setFont(new Font("Constantia", Font.PLAIN, 10)); 
		g.drawString("Anti-Ban Status: " + (antiban.getStatus().equals("") ? "Idle" : antiban.getStatus()), 10, Client.getViewportHeight() - 160);
	}
	
	@Override
	public void onExit() {
		gui.dispose();
	}
	
	public Status getStatus() {
		
		if (Inventory.isFull()) {
			if (cook && Inventory.contains(new Filter<Item>() {

				@Override
				public boolean match(Item arg0) {
					return rawFish.contains(arg0.getName());
				}
				
			})) {
				return Status.COOKING;
			} else if ((drop && Inventory.contains(new Filter<Item>() {

				@Override
				public boolean match(Item arg0) {
					return rawFish.contains(arg0.getName()) || cookedFish.contains(arg0.getName());
				}
				
			})) || (cook && Inventory.contains("Burnt fish"))) {
				return Status.DROPPING;
			} else if (bank) {
				return Status.BANK;
			} 
		} else {
			return Status.FISHING;
		}
		
		return Status.IDLE;
	}
	
	 public static String formatNumber(long number) {
	        char[] suffix = {' ', 'k', 'M', 'B', 'T', 'P', 'E'};
	        long numValue = number;
	        int value = (int) Math.floor(Math.log10(numValue));
	        int base = value / 3;
	        if (value >= 3 && base < suffix.length) {
	            return new DecimalFormat("#0.0").format(numValue / Math.pow(10, base * 3)) + suffix[base];
	        } else {
	            return new DecimalFormat("#,##0").format(numValue);
	        }
	}
	
	public static String formatSeconds(int timeInSeconds)
	{
	    int secondsLeft = timeInSeconds % 3600 % 60;
	    int minutes = (int) Math.floor(timeInSeconds % 3600 / 60);
	    int hours = (int) Math.floor(timeInSeconds / 3600);

	    String HH = ((hours       < 10) ? "0" : "") + hours;
	    String MM = ((minutes     < 10) ? "0" : "") + minutes;
	    String SS = ((secondsLeft < 10) ? "0" : "") + secondsLeft;

	    return HH + ":" + MM + ":" + SS;
	}
	
	private Image getImage(String url) {
		try {
			return ImageIO.read(new URL(url));
		} catch (IOException e) { }
		return null;
	 }
	
	@Override
	public void onGained(ExperienceEvent event) {
		if (event.getSkill().equals(Skill.COOKING)) {
			timeSinceLastCook = 0;
		}
	}
	
	
	

}
