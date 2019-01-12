package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.shared.constants.PlayerAction;
import javafx.util.Pair;
import net.ildar.wurm.Mod;
import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class TreeCutterBot extends Bot{
    private float staminaThreshold;
    private int maxActions;

    private TreeAge minTreeAge;
    private String treeType;
    private boolean bushCutting;
    private boolean sproutCutting;

    private long hatchetId;
    private long lastActionFinishedTime;
    private Byte[] sproutingAgeId = {7,9,11,13};

    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private List<Pair<Integer, Integer>> queuedTiles = new ArrayList<>();

    public TreeCutterBot(){
        registerInputHandler(InputKey.s, this::setStaminaThreshold);
        registerInputHandler(InputKey.c, this::setMaxActions);
        registerInputHandler(InputKey.area, this::toggleAreaMode);
        registerInputHandler(InputKey.area_speed, this::setAreaModeSpeedChange);
        registerInputHandler(InputKey.a, this::setMinAge);
        registerInputHandler(InputKey.al, input-> showAgesList());
        registerInputHandler(InputKey.tt, this::setTreeType);
        registerInputHandler(InputKey.b, input-> toggleBushCutting());
        registerInputHandler(InputKey.sp, input-> toggleSproutCutting());

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);

        bushCutting = false;
        sproutCutting = true;
        minTreeAge=TreeAge.any;
        treeType="";
    }

    @Override
    public void work() throws Exception {
        setStaminaThreshold(0.96f);
        String[] maxActionsNum={""+Utils.getMaxActionNumber()};
        setMaxActions(maxActionsNum);
        World world = Mod.hud.getWorld();
        PlayerObj player = world.getPlayer();
        lastActionFinishedTime = System.currentTimeMillis();

        InventoryMetaItem hatchet = Utils.getInventoryItem("hatchet");

        if (hatchet == null) {
            Utils.consolePrint("You don't have a hatchet! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        } else {
            hatchetId = hatchet.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " with QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
        }
        CreationWindow creationWindow = Mod.hud.getCreationWindow();
        Object progressBar = ReflectionUtil.getPrivateField(creationWindow, ReflectionUtil.getField(creationWindow.getClass(), "progressBar"));

        while (isActive()) {
            float progress = ReflectionUtil.getPrivateField(progressBar, ReflectionUtil.getField(progressBar.getClass(), "progress"));

            float stamina = player.getStamina();
            float damage = player.getDamage();

            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 10000 && (stamina + damage) > staminaThreshold)
                queuedTiles.clear();

            if ((stamina + damage) > staminaThreshold && queuedTiles.size() == 0) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int tileIndex = -1;

                while (++tileIndex < 9 && queuedTiles.size() < maxActions){
                    Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (queuedTiles.contains(coordsPair))
                        continue;
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);


                    if (tileType.isTree() || tileType.isBush() && bushCutting) {
                        FoliageAge fage = FoliageAge.getFoliageAge(tileData);
                        TreeData.TreeType ttype = tileType.getTreeType(tileData);

                        boolean isRightAge=fage.getAgeId() >= minTreeAge.id;
                        boolean isCutSprouts = sproutCutting || !Arrays.asList(sproutingAgeId).contains(fage.getAgeId());
                        boolean isRightType = treeType.equals("") || treeType.contains(TreeData.TreeType.fromInt(ttype.getTypeId()).toString().toLowerCase());

                        if(isRightAge && isCutSprouts && isRightType){
                            world.getServerConnection().sendAction(hatchetId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CUT_DOWN);
                            lastActionFinishedTime = System.currentTimeMillis();
                            queuedTiles.add(coordsPair);
                        }
                    }
                }
                if (queuedTiles.size() == 0 && areaAssistant.areaTourActivated() && progress == 0f)
                    areaAssistant.areaNextPosition();

            }
            sleep(timeout);
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(TreeCutterBot.InputKey.s);
        else {
            try {
                float threshold = Float.parseFloat(input[0]);
                setStaminaThreshold(threshold);
            } catch (Exception e) {
                Utils.consolePrint("Wrong threshold value!");
            }
        }
    }

    private void setTreeType(String[] strings) {
        if (strings == null ) {
            printInputKeyUsageString(TreeCutterBot.InputKey.tt);
            return;
        }

        treeType = String.join(" ", strings).toLowerCase();
        Utils.consolePrint("The bot cut " +treeType);
    }
    private void toggleBushCutting() {
        bushCutting=!bushCutting;
        if (bushCutting)
            Utils.consolePrint("Bushes cutting is on!");
        else
            Utils.consolePrint("Bushes cutting is off!");
    }
    private void toggleSproutCutting() {
        sproutCutting = !sproutCutting;
        if (sproutCutting)
            Utils.consolePrint("Sprouting trees cutting is on!");
        else
            Utils.consolePrint("Sprouting trees cutting is off!");
    }
    private void setMinAge(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(TreeCutterBot.InputKey.a);
            return;
        }
        minTreeAge = TreeAge.getByNameOrAbbreviation(input[0]);

        Utils.consolePrint("Minimal tree age set to " +minTreeAge.name+"!");
    }

    private void setMaxActions(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(TreeCutterBot.InputKey.c);
            return;
        }
        this.maxActions = Integer.parseInt(input[0]);
        Utils.consolePrint(getClass().getSimpleName() + " will do " + maxActions + " chops each time");
    }

    private void setStaminaThreshold(float s) {
        staminaThreshold = s;
        Utils.consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    private void toggleAreaMode(String []input) {
        boolean successfullAreaModeChange = areaAssistant.toggleAreaTour(input);
        if (!successfullAreaModeChange)
            printInputKeyUsageString(InputKey.area);
    }

    private void showAgesList() {
        Utils.consolePrint("Age abbreviation");
        for(TreeAge age : TreeAge.values())
            Utils.consolePrint(age.name + " " + age.name());
    }

    private void setAreaModeSpeedChange(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKey.area_speed);
            return;
        }
        float speed;
        try {
            speed = Float.parseFloat(input[0]);
            if (speed < 0) {
                Utils.consolePrint("Speed can not be negative");
                return;
            }
            if (speed == 0) {
                Utils.consolePrint("Speed can not be equal to 0");
                return;
            }
            areaAssistant.setStepTimeout((long) (1000 / speed));
            Utils.consolePrint(String.format("The speed for area mode was set to %.2f", speed));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong speed value");
        }
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        area("Toggle the area processing mode. ", "tiles_ahead tiles_to_the_right"),
        tt("Set tree types for chopping. Chop all trees by default", "birch oak"),
        c("Set chops number", "1"),
        a("Set minimal tree age for chopping. Chop all trees by default", "ov"),
        al("Get ages abbreviation list", ""),
        b("Toggle bush cutting. Disabled by default", ""),
        sp("Toggle sprouting trees cutting. Enabled by default", ""),
        area_speed("Set the speed of moving for area mode. Default value is 1 second per tile.", "speed(float value)");

        public String description;
        public String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }
    }

    private enum TreeAge {
        //any, mature, old, very old, overaged, shivered
        any(0,"any"),
        m(4,"mature"),
        o(8,"old"),
        vo(12,"very old"),
        oa(14,"overaged"),
        s(15,"shriveled");

        TreeAge(int id, String name){
            this.id=id;
            this.name=name;
        }

        public int id;
        public String name;

        static TreeAge getByNameOrAbbreviation(String input) {
            for (TreeAge treeAge : values())
                if (treeAge.name().equals(input) || treeAge.name.equals(input))//name() is collection element name, not name parameter
                    return treeAge;
            return any;
        }
    }

}
