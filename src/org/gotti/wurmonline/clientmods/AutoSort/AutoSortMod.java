package org.gotti.wurmonline.clientmods.AutoSort;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.game.inventory.InventoryMetaWindowManager;
import com.wurmonline.client.game.inventory.InventoryMetaWindowView;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;
import org.gotti.wurmunlimited.modsupport.console.ConsoleListener;
import org.gotti.wurmunlimited.modsupport.console.ModConsole;


public class AutoSortMod implements WurmClientMod, Initable, PreInitable, ConsoleListener {
    private static final int MAX_MOVEMENT_OPERATIONS = 30;
    private static final String FILTER_FILENAME = "sortingFilters.ser";
    
    private static final Logger logger = Logger.getLogger(AutoSortMod.class.getName());
    private World world;
    
    private Map<String, String> filters;

    @Override
    public void init() {
        // com.wurmonline.client.renderer.gui.HeadsUpDisplay.init(int, int)
        HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V",
            new InvocationHandlerFactory() {
                @Override
                public InvocationHandler createInvocationHandler() {
                    return new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            method.invoke(proxy, args);
                            initSortMod((HeadsUpDisplay) proxy);
                            return null;
                        }
                    };
                }
        });

        ModConsole.addConsoleListener(this);
        
        filters = loadFilter();
    }
    
    private void saveFilter() {
        try {
            FileOutputStream fileOut = new FileOutputStream(FILTER_FILENAME, false);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(filters);
            out.close();
            fileOut.close();
        } catch (FileNotFoundException ex) {
            // Meep
        } catch (IOException ex) {
            // Beep
        }
    }
    
    private Map<String, String> loadFilter() {
        Map<String,String> filterToReturn = new LinkedHashMap<>();
        try {
            FileInputStream fileIn = new FileInputStream(FILTER_FILENAME);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            filterToReturn = (Map<String, String>) in.readObject();
            in.close();
            fileIn.close();
        } catch (FileNotFoundException ex) {
            // Beep beep I'm a jeep
        } catch (IOException ex) {
            // Terrible programming right here
        } catch (ClassNotFoundException ex) {
            // This guy isn't actually doing anything with exceptions; HERESY
        }
        return filterToReturn;
    }
    
    private void initSortMod(HeadsUpDisplay hud) {
        new Runnable() {
            @Override
            public void run() {
                try {
                    world = ReflectionUtil.getPrivateField(hud, ReflectionUtil.getField(hud.getClass(), "world"));
                }
                catch (IllegalArgumentException | IllegalAccessException | ClassCastException | NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        }.run();
    }
    
    public List<InventoryMetaItem> recursiveSearch(InventoryMetaItem node, String searchStr) {
        List<InventoryMetaItem> returnList = new ArrayList<>();
        
        if(node.getDisplayName().toLowerCase().contains(searchStr.toLowerCase()))
            returnList.add(node);
        
        node.getChildren().forEach(childNode -> returnList.addAll(recursiveSearch(childNode, searchStr)));
        
        return returnList;
    }
    
    // Debug method
    public void recursivePrint(InventoryMetaItem node, int level) {
        StringBuilder prefix = new StringBuilder();
        if(level > 0) {
            char[] repeat = new char[level];
            Arrays.fill(repeat, '-');
            prefix.append(repeat);
        }
        
        if(node.getChildren().size() > 0)
            prefix.append("[+]");
        logger.log(Level.INFO, prefix + " " + node.getDisplayName());
        
        node.getChildren().forEach(childNode -> recursivePrint(childNode, level+1));
    }
    
    private void sortInventory(InventoryMetaItem node) {
        (new Thread(){
                @Override
                public void run() {
                    for(Map.Entry<String, String> entry : filters.entrySet()) {
                        List<InventoryMetaItem> containers = recursiveSearch(node, entry.getValue());
                        List<InventoryMetaItem> items = recursiveSearch(node, entry.getKey());

                        int loopCounter = 0;
                        while(loopCounter < 30) {

                            List<InventoryMetaItem> itemsToRemove = new ArrayList<>();
                            for(InventoryMetaItem item : items) {
                                if(isItemSorted(item, containers)) {
                                    itemsToRemove.add(item);
                                }
                            }

                            for(InventoryMetaItem item : itemsToRemove) {
                                items.remove(item);
                            }

                            // No items left to shuffle around! Abort loop
                            if(items.size() <= 0)
                                break;

                            InventoryMetaItem container = findSuitableContainer(containers);

                            if(container != null) {
                                try {
                                    int itemCount = 0;
                                    int slotsLeft = 100 - container.getChildren().size();
                                    int moveAmount = slotsLeft > items.size() ? items.size() : slotsLeft;
                                    long[] itemIdArr = new long[moveAmount];
                                    for(InventoryMetaItem item : items) {
                                        if(slotsLeft > 0) {
                                            itemIdArr[itemCount] = item.getId();
                                            itemCount++;
                                            slotsLeft--;
                                        } else {
                                            break;
                                        }
                                    }
                                    logger.log(Level.INFO, "Moving {0} item(s) into {1}[{2}]", new Object[]{itemIdArr.length, container.getDisplayName(), container.getId()});
                                    world.getServerConnection().sendMoveSomeItems(container.getId(), itemIdArr);
                                    Thread.sleep(500);
                                } catch (InterruptedException ex) {
                                    logger.log(Level.SEVERE, null, ex);
                                }
                            } else {
                                // No container left to move anything into! Abort loop
                                break;
                            }
                            loopCounter++;
                        }
                    }
                }
            }).start();
    }
    
    private InventoryMetaItem findSuitableContainer(List<InventoryMetaItem> containers) {
        for(InventoryMetaItem container : containers) {
            if(container.getChildren().size() < 100)
                return container;
        }
        return null;
    }
    
    private boolean isItemSorted(InventoryMetaItem item, List<InventoryMetaItem> containers) {
        for(InventoryMetaItem container : containers) {
            if(container.getId() == item.getParentId())
                return true;
        }
        return false;
    }

    @Override
    public boolean handleInput(String string, Boolean bln) {
        if (string.startsWith("printInventory")) {
            InventoryMetaWindowManager invManager = world.getInventoryManager();
            InventoryMetaWindowView playerInventory = invManager.getPlayerInventory();
            InventoryMetaItem rootItem = playerInventory.getRootItem();
            recursivePrint(rootItem, 0);
            bln = true;
            return true;
        }
        
        if(string.startsWith("sortInventory")) {
            InventoryMetaWindowManager invManager = world.getInventoryManager();
            InventoryMetaWindowView playerInventory = invManager.getPlayerInventory();
            InventoryMetaItem rootItem = playerInventory.getRootItem();
            for (InventoryMetaItem children : rootItem.getChildren()) {
                if(children.getDisplayName().equals("inventory")) {
                    sortInventory(children);
                }
            }
            bln = true;
            return true;
        }
        
        if(string.startsWith("addRule")) {
            String param = string.substring(7);
            String params[] = param.split("->");
            for(int i = 0; i < params.length; i++) {
                params[i] = params[i].trim().toLowerCase();
            }
            
            if(params.length != 2) {
                logger.log(Level.INFO, "Invalid amount of parameters!");
            } else {
                String value = filters.putIfAbsent(params[0], params[1]);
                if(value == null) {
                    logger.log(Level.INFO, "New sorting rule: {0} -> {1}", new Object[]{params[0], params[1]});
                    saveFilter();
                } else
                    logger.log(Level.INFO, "Sorting rule already exists for {0}: {1}", new Object[]{params[0], value});
            }
            bln = true;
            return true;
        }
        
        if(string.startsWith("listRules")) {
            if(filters.size() > 0) {
                logger.log(Level.INFO, "Sorting rules:");
                for(String key : filters.keySet()) {
                    logger.log(Level.INFO, "{0} -> {1}", new Object[]{key, filters.get(key)});
                }
            } else
                logger.log(Level.INFO, "No sorting rules found");
            bln = true;
            return true;
        }
        
        if(string.startsWith("delRule")) {
            String param = string.substring(7).trim().toLowerCase();
            
            if(param == null) {
                logger.log(Level.INFO, "No parameter defined!");
            } else {
                String value = filters.remove(param);
                if(value == null)
                    logger.log(Level.INFO, "No such filter could be found");
                else {
                    logger.log(Level.INFO, "Filter for {0} removed", new Object[]{param});
                    saveFilter();
                }
            }
            bln = true;
            return true;
        }
        return false;
    } 

    @Override
    public void preInit() {
    }
}
