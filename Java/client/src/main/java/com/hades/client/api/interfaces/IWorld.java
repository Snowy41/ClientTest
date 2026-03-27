package com.hades.client.api.interfaces;

import java.util.List;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.api.interfaces.ITileEntity;

/**
 * Universal interface for the world environment.
 */
public interface IWorld {

    /** Returns true if the world instance is currently null (not in game) */
    boolean isNull();

    /** 
     * Returns a list of wrapped universal entity objects (IEntity) loaded in the world.
     */
    List<IEntity> getLoadedEntities();
    
    /** 
     * Returns a list of wrapped TileEntity objects (chests, furnaces, etc) loaded in the world.
     */
    List<ITileEntity> getLoadedTileEntities();

    /** Returns true if the block at the given coordinates is air */
    boolean isAirBlock(int x, int y, int z);
    
    /** Returns true if the block at the given coordinates is considered solid (not air, water, grass, fire, etc.) */
    boolean isSolidBlock(int x, int y, int z);
    
    /** Returns the raw block object at the given BlockPos coordinate */
    Object getBlockAt(Object blockPos);

    /** 
     * Traces a ray from (x1,y1,z1) to (x2,y2,z2) against blocks in the world.
     * Returns a TraceResult with hit information, or TraceResult.miss() if nothing was hit.
     */
    TraceResult rayTraceBlocks(double x1, double y1, double z1, double x2, double y2, double z2);

    /**
     * Checks if the ray from (x1,y1,z1) to (x2,y2,z2) intersects any living entity's
     * expanded bounding box (expanded by 0.3 on each axis). Excludes the given raw entity.
     * Returns a TraceResult with the entity hit position, or TraceResult.miss().
     */
    TraceResult checkEntityIntercept(double x1, double y1, double z1, double x2, double y2, double z2, Object excludeRaw);

    /** Returns the world's scoreboard */
    com.hades.client.api.interfaces.scoreboard.IScoreboard getScoreboard();

    /** Returns the raw world object */
    Object getRaw();
}
