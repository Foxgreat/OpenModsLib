package openmods.config.game;

import javax.annotation.Nullable;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.ModContainer;

public interface IRegisterableBlock {
	public void setupBlock(ModContainer container, String id, Class<? extends TileEntity> tileEntity, @Nullable ItemBlock itemBlock);
}
