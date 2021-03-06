/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public interface IActionExternal extends IStatement {

	void actionActivate(TileEntity target, EnumFacing side, IStatementContainer source, IStatementParameter[] parameters);
	
}
