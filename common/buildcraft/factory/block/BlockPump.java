package buildcraft.factory.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import buildcraft.factory.tile.TilePump;
import buildcraft.lib.block.BlockBCTile_Neptune;

public class BlockPump extends BlockBCTile_Neptune {
    public BlockPump(Material material, String id) {
        super(material, id);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TilePump();
    }
}
