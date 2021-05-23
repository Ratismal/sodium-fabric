package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.lang.reflect.Array;
import java.util.concurrent.CompletableFuture;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class ChunkRenderContainer<T extends ChunkGraphicsState> {
    private final SodiumWorldRenderer worldRenderer;
    private final int chunkX, chunkY, chunkZ;

    private final T[] graphicsStates;
    private final ChunkRenderColumn<T> column;

    private ChunkRenderData data = ChunkRenderData.ABSENT;
    private CompletableFuture<Void> rebuildTask = null;

    private boolean needsRebuild;
    private boolean needsImportantRebuild;

    private boolean tickable;
    private int id;

    public ChunkRenderContainer(ChunkRenderBackend<T> backend, SodiumWorldRenderer worldRenderer, int chunkX, int chunkY, int chunkZ, ChunkRenderColumn<T> column) {
        this.worldRenderer = worldRenderer;

        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        //noinspection unchecked
        this.graphicsStates = (T[]) Array.newInstance(backend.getGraphicsStateType(), BlockRenderPass.COUNT);
        this.column = column;
    }

    /**
     * Cancels any pending tasks to rebuild the chunk. If the result of any pending tasks has not been processed yet,
     * those will also be discarded when processing finally happens.
     */
    public void cancelRebuildTask() {
        this.needsRebuild = false;
        this.needsImportantRebuild = false;

        if (this.rebuildTask != null) {
            this.rebuildTask.cancel(false);
            this.rebuildTask = null;
        }
    }

    public ChunkRenderData getData() {
        return this.data;
    }

    /**
     * @return True if the render's state is out of date with the world state
     */
    public boolean needsRebuild() {
        return this.needsRebuild;
    }

    /**
     * @return True if the render's rebuild should be performed as blocking
     */
    public boolean needsImportantRebuild() {
        return this.needsImportantRebuild;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        this.cancelRebuildTask();
        this.setData(ChunkRenderData.ABSENT);
        this.deleteGraphicsState();
    }

    private void deleteGraphicsState() {
        T[] states = this.graphicsStates;

        for (int i = 0; i < states.length; i++) {
            T state = states[i];

            if (state != null) {
                state.delete();
                states[i] = null;
            }
        }
    }

    public void setData(ChunkRenderData info) {
        if (info == null) {
            throw new NullPointerException("Mesh information must not be null");
        }

        this.worldRenderer.onChunkRenderUpdated(this.chunkX, this.chunkY, this.chunkZ, this.data, info);
        this.data = info;

        this.tickable = !info.getAnimatedSprites().isEmpty();
    }

    /**
     * Marks this render as needing an update. Important updates are scheduled as "blocking" and will prevent the next
     * frame from being rendered until the update is performed.
     * @param important True if the update is blocking, otherwise false
     */
    public boolean scheduleRebuild(boolean important) {
        boolean changed = !this.needsRebuild || (!this.needsImportantRebuild && important);

        this.needsImportantRebuild = important;
        this.needsRebuild = true;

        return changed;
    }

    /**
     * @return True if the chunk render contains no data, otherwise false
     */
    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    /**
     * Returns the chunk section position which this render refers to in the world.
     */
    public ChunkSectionPos getChunkPos() {
        return ChunkSectionPos.from(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * Tests if the given chunk render is visible within the provided frustum.
     * @param frustum The frustum to test against
     * @return True if visible, otherwise false
     */
    public boolean isOutsideFrustum(FrustumExtended frustum) {
        float x = this.getOriginX();
        float y = this.getOriginY();
        float z = this.getOriginZ();

        return !frustum.fastAabbTest(x, y, z, x + 16.0f, y + 16.0f, z + 16.0f);
    }

    /**
     * Ensures that all resources attached to the given chunk render are "ticked" forward. This should be called every
     * time before this render is drawn if {@link ChunkRenderContainer#isTickable()} is true.
     */
    public void tick() {
        for (Sprite sprite : this.data.getAnimatedSprites()) {
            SpriteUtil.markSpriteActive(sprite);
        }
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    public int getRenderX() {
        return this.getOriginX() - 8;
    }

    public int getRenderY() {
        return this.getOriginY() - 8;
    }

    public int getRenderZ() {
        return this.getOriginZ() - 8;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the given position
     */
    public double getSquaredDistance(double x, double y, double z) {
        double xDist = x - this.getCenterX();
        double yDist = y - this.getCenterY();
        double zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    private double getCenterX() {
        return this.getOriginX() + 8.0D;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    private double getCenterY() {
        return this.getOriginY() + 8.0D;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    private double getCenterZ() {
        return this.getOriginZ() + 8.0D;
    }

    public BlockPos getRenderOrigin() {
        return new BlockPos(this.getRenderX(), this.getRenderY(), this.getRenderZ());
    }

    public T[] getGraphicsStates() {
        return this.graphicsStates;
    }

    public void setGraphicsState(BlockRenderPass pass, T state) {
        this.graphicsStates[pass.ordinal()] = state;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the given position
     */
    public double getSquaredDistanceXZ(double x, double z) {
        double xDist = x - this.getCenterX();
        double zDist = z - this.getCenterZ();

        return (xDist * xDist) + (zDist * zDist);
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public ChunkRenderBounds getBounds() {
        return this.data.getBounds();
    }

    public T getGraphicsState(BlockRenderPass pass) {
        return this.graphicsStates[pass.ordinal()];
    }

    public boolean isTickable() {
        return this.tickable;
    }

    public int getFacesWithData() {
        return this.data.getFacesWithData();
    }

    public boolean canRebuild() {
        return this.column.areNeighborsPresent();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
Skip to content
Pull requests
Issues
Marketplace
Explore
@machinesmith42
machinesmith42 /
sodium-fabric
forked from qouteall/sodium-fabric

1
1

    264

Code
Pull requests 2
Actions
Projects
Security
Insights

    Settings

sodium-fabric/src/main/java/me/jellysquid/mods/sodium/client/render/chunk/
in
pr/14-update

1

package me.jellysquid.mods.sodium.client.render.chunk;

2

​

3

import me.jellysquid.mods.sodium.client.SodiumHooks;

4

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;

5

import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;

6

import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;

7

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

8

import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;

9

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;

10

​

11

import me.jellysquid.mods.sodium.common.util.DirectionUtil;

12

import net.minecraft.client.render.Frustum;

13

import me.jellysquid.mods.sodium.common.util.collections.TrackedArrayItem;

14

​

15

import net.minecraft.client.texture.Sprite;

16

import net.minecraft.util.math.BlockPos;

17

import net.minecraft.util.math.Box;

18

import net.minecraft.util.math.ChunkSectionPos;

19

​

20

import java.lang.reflect.Array;

21

import java.util.concurrent.CompletableFuture;

22

​

23

/**

24

 * The render state object for a chunk section. This contains all the graphics state for each render pass along with

25

 * data about the render in the chunk visibility graph.

26

 */

27

public class ChunkRenderContainer<T extends ChunkGraphicsState> {

28

    private final SodiumWorldRenderer worldRenderer;

29

    private final int chunkX, chunkY, chunkZ;

30

​

31

    private final T[] graphicsStates;

32

    private final ChunkRenderColumn<T> column;

33

​

34

    private ChunkRenderData data = ChunkRenderData.ABSENT;

35

    private CompletableFuture<Void> rebuildTask = null;

36

​

37

    private boolean needsRebuild;

38

    private boolean needsImportantRebuild;

39

​

40

    private boolean tickable;

41

    private int id;

42

​

43

​

44

    public ChunkRenderContainer(ChunkRenderBackend<T> backend, SodiumWorldRenderer worldRenderer, int chunkX, int chunkY, int chunkZ, ChunkRenderColumn<T> column) {

45

​

46

        this.worldRenderer = worldRenderer;

@machinesmith42
Commit changes
Commit summary
Optional extended description
Commit directly to the pr/14-update branch.
Create a new branch for this commit and start a pull request. Learn more about pull requests.

    © 2021 GitHub, Inc.
    Terms
    Privacy
    Security
    Status
    Docs

    Contact GitHub
    Pricing
    API
    Training
    Blog
    About

