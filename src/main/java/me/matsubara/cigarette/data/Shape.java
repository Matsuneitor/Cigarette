package me.matsubara.cigarette.data;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Strings;
import me.matsubara.cigarette.CigarettePlugin;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;

@SuppressWarnings("unused")
public final class Shape {

    private final CigarettePlugin plugin;

    private final String name;
    private final boolean shaped;
    private final List<String> ingredients;
    private final List<String> shape;

    private Recipe recipe;

    public Shape(CigarettePlugin plugin, String name, boolean shaped, List<String> ingredients, List<String> shape, ItemStack result) {
        this.plugin = plugin;
        this.name = name;
        this.shaped = shaped;
        this.ingredients = ingredients;
        this.shape = shape;
        register(result);
    }

    @SuppressWarnings("deprecation")
    public void register(ItemStack item) {
        // Since 1.12, a namespaced key is required.
        if (XReflection.MINOR_NUMBER > 11) {
            NamespacedKey key = new NamespacedKey(plugin, "cigarette_" + name);
            recipe = shaped ? new ShapedRecipe(key, item) : new ShapelessRecipe(key, item);
        } else {
            recipe = shaped ? new ShapedRecipe(item) : new ShapelessRecipe(item);
        }

        if (shaped) {
            ((ShapedRecipe) recipe).shape(shape.toArray(new String[0]));
        }

        for (String ingredient : ingredients) {
            if (Strings.isNullOrEmpty(ingredient) || ingredient.equalsIgnoreCase("none")) continue;
            String[] split = StringUtils.split(StringUtils.deleteWhitespace(ingredient), ',');
            if (split.length == 0) split = StringUtils.split(ingredient, ' ');

            Material type = Material.valueOf(split[0]);

            char key = ' ';

            if (split.length > 1) {
                key = split[1].charAt(0);
            }

            if (shaped) {
                // Empty space are used for AIR.
                if (key == ' ') continue;
                ((ShapedRecipe) recipe).setIngredient(key, type.asItemType());
            } else {
                ((ShapelessRecipe) recipe).addIngredient(type.asItemType());
            }
        }

        Bukkit.addRecipe(recipe);
    }

    public Recipe getRecipe() {
        return recipe;
    }
}
