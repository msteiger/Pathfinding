/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.pathfinding;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.CoreRegistry;
import org.terasology.math.Vector3i;
import org.terasology.pathfinding.maze.MazeGenerator;
import org.terasology.world.WorldBiomeProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.Chunk;
import org.terasology.world.generator.FirstPassGenerator;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author synopia
 */
public class MazeChunkGenerator implements FirstPassGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MazeChunkGenerator.class);

    public BlockManager blockManager = CoreRegistry.get(BlockManager.class);
    public Block air = BlockManager.getAir();
    public Block ground = blockManager.getBlock("engine:Dirt");
    public Block torch = blockManager.getBlock("engine:Torch.top");

    private int width;
    private int height;
    private List<BitSet[]> mazes;
    private List<Vector3i> stairs;
    private int lastStairX = 0;
    private int lastStairY = 0;
    private int startHeight;
    private Random random;

    public MazeChunkGenerator(int width, int height, int levels, int startHeight, long seed) {
        this.width = width;
        this.height = height;
        this.startHeight = startHeight;
        this.random = new Random(seed);

        mazes = Lists.newArrayList();
        stairs = Lists.newArrayList();
        for (int i = 0; i < levels; i++) {
            mazes.add(createMaze());
        }
        for (int i = 0; i < mazes.size() - 1; i++) {
            insertStairs(i, mazes.get(i), mazes.get(i + 1));
        }
    }

    private void insertStairs(int level, BitSet[] level1, BitSet[] level2) {
        int free = 0;
        int stairX = -1;
        int stairY = -1;
        for (int y = lastStairY; y < height; y++) {
            for (int x = lastStairX; x < width; x++) {
                if (level1[y].get(x) || level2[y].get(x)) {
                    free = 0;
                } else {
                    free++;
                }
                if (free == 5) {
                    stairX = x;
                    stairY = y;
                    break;
                }
            }
            if (stairX != -1 && stairY != -1) {
                break;
            }
            lastStairX = 0;
        }
        if (stairX != -1 && stairY != -1) {
            stairs.add(new Vector3i(stairX - 4, level, stairY));
            lastStairX = stairX;
            lastStairY = stairY;
        }
    }

    private BitSet[] createMaze() {
        BitSet[] maze = new BitSet[height];
        for (int i = 0; i < maze.length; i++) {
            maze[i] = new BitSet(width);
        }
        MazeGenerator generator = new MazeGenerator(width, height, random);
        generator.display(maze);
        return maze;
    }

    @Override
    public void generateChunk(Chunk chunk) {
        int groundHeight = startHeight;
        int offsetX = chunk.getChunkWorldPosX();
        int offsetZ = chunk.getChunkWorldPosZ();
        int y = groundHeight;
        if (offsetX >= 0 && offsetZ >= 0 && offsetX < width && offsetZ < height) {
            logger.info("generate maze chunk");
            for (BitSet[] maze : mazes) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    for (int x = 0; x < Chunk.SIZE_X; x++) {
                        int mazeX = offsetX + x;
                        int mazeZ = offsetZ + z;
                        chunk.setBlock(x, y, z, ground);

                        if (mazeX < width && mazeZ < height && maze[mazeZ].get(mazeX)) {
                            chunk.setBlock(x, y + 1, z, ground);
                            chunk.setBlock(x, y + 2, z, ground);
                        } else {
                            if ((mazeX % 3) == 1 && (mazeZ % 3) == 1) {
                                chunk.setBlock(x, y + 1, z, torch);
                            } else {
                                chunk.setBlock(x, y + 1, z, air);
                            }
                            chunk.setBlock(x, y + 2, z, air);
                        }

                    }
                }
                y += 3;
            }
            for (int i = 0; i < stairs.size(); i++) {
                Vector3i stairPos = stairs.get(i);
                int chunkPosX = stairPos.x - offsetX;
                int chunkPosY = groundHeight + stairPos.y * 3;
                int chunkPosZ = stairPos.z - offsetZ;
                if (chunkPosX >= 0 && chunkPosZ >= 0 && chunkPosX < Chunk.SIZE_X && chunkPosZ < Chunk.SIZE_Z) {
                    chunk.setBlock(chunkPosX, chunkPosY + 1, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 2, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 3, chunkPosZ, air);
                }
                chunkPosX++;
                if (chunkPosX >= 0 && chunkPosZ >= 0 && chunkPosX < Chunk.SIZE_X && chunkPosZ < Chunk.SIZE_Z) {
                    chunk.setBlock(chunkPosX, chunkPosY + 1, chunkPosZ, ground);
                    chunk.setBlock(chunkPosX, chunkPosY + 2, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 3, chunkPosZ, air);
                }
                chunkPosX++;
                if (chunkPosX >= 0 && chunkPosZ >= 0 && chunkPosX < Chunk.SIZE_X && chunkPosZ < Chunk.SIZE_Z) {
                    chunk.setBlock(chunkPosX, chunkPosY + 1, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 2, chunkPosZ, ground);
                    chunk.setBlock(chunkPosX, chunkPosY + 3, chunkPosZ, air);
                }
                chunkPosX++;
                if (chunkPosX >= 0 && chunkPosZ >= 0 && chunkPosX < Chunk.SIZE_X && chunkPosZ < Chunk.SIZE_Z) {
                    chunk.setBlock(chunkPosX, chunkPosY + 1, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 2, chunkPosZ, air);
                    chunk.setBlock(chunkPosX, chunkPosY + 3, chunkPosZ, ground);
                }
            }
        }
    }

    @Override
    public void setWorldSeed(String seed) {

    }

    @Override
    public void setWorldBiomeProvider(WorldBiomeProvider biomeProvider) {

    }

    @Override
    public Map<String, String> getInitParameters() {
        return null;
    }

    @Override
    public void setInitParameters(Map<String, String> initParameters) {

    }
}
