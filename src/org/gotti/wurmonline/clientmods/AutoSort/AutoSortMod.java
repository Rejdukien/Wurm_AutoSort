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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    
    private Map<FilterItem, FilterItem> filters;

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
        } catch (Exception ex) {
            logger.severe(ex.toString());
        }
    }
    
    private Map<FilterItem, FilterItem> loadFilter() {
        Map<FilterItem,FilterItem> filterToReturn = new LinkedHashMap<>();
        try {
            FileInputStream fileIn = new FileInputStream(FILTER_FILENAME);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            filterToReturn = (Map<FilterItem, FilterItem>) in.readObject();
            in.close();
            fileIn.close();
        } catch (Exception ex) {
            logger.severe(ex.toString());
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
        logger.log(Level.INFO, "{0} {1}", new Object[]{prefix, node.getDisplayName()});
        
        node.getChildren().forEach(childNode -> recursivePrint(childNode, level+1));
    }
    
    // TODO
    private void sortInventory(List<InventoryMetaItem> nodes) {
        (new Thread(){
                @Override
                public void run() {
                    for(Map.Entry<FilterItem, FilterItem> entry : filters.entrySet()) {
                        logger.log(Level.INFO, "Loop for: {0}", entry.getKey().toString());
                        List<InventoryMetaItem> containers = new ArrayList<>();
                        List<InventoryMetaItem> items = new ArrayList<>();
                        
                        if(entry.getValue().isApplyWorld()) {
                            for(int i = 1; i < nodes.size(); i++) {
                                containers.addAll(recursiveSearch(nodes.get(i), entry.getValue().getItemName()));
                            }
                        } else {
                            containers.addAll(recursiveSearch(nodes.get(0), entry.getValue().getItemName()));
                        }
                        
                        if(entry.getKey().isApplyWorld()) {
                            for(int i = 1; i < nodes.size(); i++) {
                                items.addAll(recursiveSearch(nodes.get(i), entry.getKey().getItemName()));
                            }
                        } else {
                            items.addAll(recursiveSearch(nodes.get(0), entry.getKey().getItemName()));
                        }
                        
                        logger.log(Level.INFO, "{0} items, {1} containers matching", new Object[]{items.size(), containers.size()});

                        int loopCounter = 0;
                        while(loopCounter < 30) {

                            List<InventoryMetaItem> itemsToRemove = new ArrayList<>();
                            for(InventoryMetaItem item : items) {
                                if(isItemSorted(item, containers)) {
                                    itemsToRemove.add(item);
                                    logger.log(Level.INFO, "item {0}({1}) is sorted, removing...", new Object[]{item.getDisplayName(), item.getId()});
                                }
                            }

                            itemsToRemove.stream().forEach((item) -> {
                                items.remove(item);
                            });

                            // No items left to shuffle around! Abort loop
                            if(items.size() <= 0)
                                break;

                            //InventoryMetaItem container = findSuitableContainer(containers, entry.getKey().getItemName(), entry.getValue().getQuantity());
                            containers = findAllSuitableContainers(containers, entry.getKey().getItemName(), entry.getValue().getQuantity());

                            if(containers.size() > 0) {
                                InventoryMetaItem container = containers.get(0);
                                int itemCount = 0;
                                int slotsLeft = 100 - container.getChildren().size();
                                int moveAmount = slotsLeft > items.size() ? items.size() : slotsLeft;
                                if(entry.getValue().getQuantity() != 100) {
                                    int countOfItemInContainer = countOfItemInContainer(container, entry.getValue().getItemName());
                                    moveAmount = (slotsLeft > entry.getValue().getQuantity() - countOfItemInContainer) && (slotsLeft > items.size()) ? 
                                            (entry.getValue().getQuantity() - countOfItemInContainer > items.size() ? items.size() : entry.getValue().getQuantity() - countOfItemInContainer) :
                                            slotsLeft;
                                }

                                logger.log(Level.INFO, "Found suitable container with {0} slots left, trying to move {1} item(s)", new Object[]{slotsLeft, moveAmount});

                                long[] itemIdArr = new long[moveAmount];
                                List<InventoryMetaItem> itemsToRemove2 = new ArrayList<>();
                                for(InventoryMetaItem item : items) {
                                    if(moveAmount > 0) {
                                        itemIdArr[itemCount] = item.getId();
                                        itemCount++;
                                        moveAmount--;
                                        itemsToRemove2.add(item);
                                    } else {
                                        break;
                                    }
                                }
                                items.removeAll(itemsToRemove2);
                                
                                containers.remove(container);
                                
                                logger.log(Level.INFO, "Moving {0} item(s) into {1}[{2}]", new Object[]{itemIdArr.length, container.getDisplayName(), container.getId()});
                                world.getServerConnection().sendMoveSomeItems(container.getId(), itemIdArr);
                                
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(AutoSortMod.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                // No container left to move anything into! Abort loop
                                break;
                            } // if containers > 0
                            loopCounter++;
                        } // while loopCounter
                    } // for each filtermap entry
                }
            }).start();
    }
    
    private int countOfItemInContainer(InventoryMetaItem container, String itemName) {
        int countOfItem = 0;
        for(InventoryMetaItem item : container.getChildren())
            if(item.getDisplayName().toLowerCase().contains(itemName))
                countOfItem++;
        
        return countOfItem;
    }
    
    private List<InventoryMetaItem> findAllSuitableContainers(List<InventoryMetaItem> containers, String itemName, int fillToQuantity) {
        List<InventoryMetaItem> returnContainers = new ArrayList<>();
        for(InventoryMetaItem container : containers) {
            if(container.getChildren().size() < 100) {
                if(fillToQuantity != 100) {
                    if(countOfItemInContainer(container, itemName) < fillToQuantity)
                        returnContainers.add(container);
                } else {
                    returnContainers.add(container);
                }
            }
        }
        return returnContainers;
    }
    
    private boolean isItemSorted(InventoryMetaItem item, List<InventoryMetaItem> containers) {
        if (containers.stream().anyMatch((container) -> (container.getId() == item.getParentId()))) {
            return true;
        }
        return false;
    }
    
    private void showInventories() {
        InventoryMetaWindowManager invManager = world.getInventoryManager();
        try {
            Map<Long, InventoryMetaWindowView> inventories = ReflectionUtil.getPrivateField(invManager, ReflectionUtil.getField(InventoryMetaWindowManager.class, "inventoryWindows"));
            for(Map.Entry<Long, InventoryMetaWindowView> entry : inventories.entrySet()) {
                if(entry.getKey() > 0) {
                    logger.info("##########");
                    logger.log(Level.INFO, "{0} {1}", new Object[]{entry.getKey(), entry.getValue().getWindowName()});
                    recursivePrint(entry.getValue().getRootItem(), 0);
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(AutoSortMod.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private FilterItem parseFilterItemString(String str) {
        String itemName = "";
        int quantity = 100;
        boolean applyWorld = false;
        
        Pattern pattern = Pattern.compile("(\\[(?<options>.*?)\\])?(?<name>.+)");
        Matcher matcher = pattern.matcher(str);
        boolean matches = matcher.matches();
        
        String options = matcher.group("options");
        itemName = matcher.group("name");
        
        if(options != null) {
            Matcher matcherOptions = Pattern.compile("\\d+").matcher(options);
            if(matcherOptions.find()) {
                quantity = Integer.parseInt(matcherOptions.group());
            }
            applyWorld = options.contains("world");
        }
        
        return new FilterItem(itemName.trim(), applyWorld, quantity);
    }

    @Override
    public boolean handleInput(String string, Boolean bln) {
        if (string.startsWith("printInventory")) {
            InventoryMetaWindowManager invManager = world.getInventoryManager();
            InventoryMetaWindowView playerInventory = invManager.getPlayerInventory();
            InventoryMetaItem rootItem = playerInventory.getRootItem();
            recursivePrint(rootItem, 0);
            showInventories();
            bln = true;
            return true;
        }
        
        if(string.startsWith("sortInventory")) {
            InventoryMetaWindowManager invManager = world.getInventoryManager();
            InventoryMetaWindowView playerInventory = invManager.getPlayerInventory();
            InventoryMetaItem rootItem = playerInventory.getRootItem();
            
            List<InventoryMetaItem> nodes = new ArrayList<>();
            
            for (InventoryMetaItem children : rootItem.getChildren()) {
                if(children.getDisplayName().equals("inventory")) {
                    nodes.add(children);
                }
            }
            
            try {
                Map<Long, InventoryMetaWindowView> inventories = ReflectionUtil.getPrivateField(invManager, ReflectionUtil.getField(InventoryMetaWindowManager.class, "inventoryWindows"));
                for(Map.Entry<Long, InventoryMetaWindowView> entry : inventories.entrySet()) {
                    if(entry.getKey() > 0)
                        nodes.add(entry.getValue().getRootItem());
                }
            } catch (Exception ex) {
                Logger.getLogger(AutoSortMod.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            sortInventory(nodes);
            
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
                FilterItem returned = filters.putIfAbsent(parseFilterItemString(params[0]), parseFilterItemString(params[1]));
                if(returned == null) {
                    logger.log(Level.INFO, "New sorting rule: {0} -> {1}", new Object[]{params[0], params[1]});
                    saveFilter();
                } else
                    logger.log(Level.INFO, "Sorting rule already exists for {0}", new Object[]{params[0]});
            }
            bln = true;
            return true;
        }
        
        if(string.startsWith("listRules")) {
            if(filters.size() > 0) {
                logger.log(Level.INFO, "Sorting rules:");
                filters.entrySet().stream().forEach((entrySet) -> {
                    logger.log(Level.INFO, "{0} -> {1}", new Object[]{entrySet.getKey().toString(), entrySet.getValue().toString()});
                });
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
                FilterItem toBeSearched = parseFilterItemString(param);
                FilterItem toBeDeleted = null;
                for(Map.Entry<FilterItem, FilterItem> entrySet : filters.entrySet()) {
                    if(entrySet.getKey().toString().equals(toBeSearched.toString()))
                        toBeDeleted = entrySet.getKey();
                }
                
                if(toBeDeleted == null)
                    logger.log(Level.INFO, "No such filter could be found ({0})", toBeSearched.toString());
                else {
                    filters.remove(toBeDeleted);
                    logger.log(Level.INFO, "Filter for {0} removed", new Object[]{toBeDeleted.toString()});
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
