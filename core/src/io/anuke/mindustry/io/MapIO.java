package io.anuke.mindustry.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.io.MapTileData.TileDataMarker;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.ColorMapper;
import io.anuke.mindustry.world.ColorMapper.BlockPair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static io.anuke.mindustry.Vars.customMapDirectory;
import static io.anuke.mindustry.Vars.mapExtension;

/**Reads and writes map files.*/
//TODO GWT support
//TODO map header that maps block names to IDs for backwards compatibility
public class MapIO {
    private static final int version = 0;
    private static final IntIntMap defaultBlockMap = new IntIntMap();

    static{

        for(Block block : Block.getAllBlocks()){
            defaultBlockMap.put(block.id, block.id);
        }
    }

    public static Pixmap generatePixmap(MapTileData data){
        Pixmap pixmap = new Pixmap(data.width(), data.height(), Format.RGBA8888);
        data.position(0, 0);

        for(int y = 0; y < data.height(); y ++){
            for(int x = 0; x < data.width(); x ++){
                TileDataMarker marker = data.read();
                Block floor = Block.getByID(marker.floor);
                Block wall = Block.getByID(marker.wall);
                int wallc = ColorMapper.getColor(wall);
                if(wallc == 0 && (wall.update || wall.solid || wall.breakable)) wallc = Color.rgba8888(Team.values()[marker.team].color);
                pixmap.drawPixel(x, pixmap.getHeight() - 1 - y, wallc == 0 ? ColorMapper.getColor(floor) : wallc);
            }
        }

        return pixmap;
    }

    public static MapTileData readPixmap(Pixmap pixmap){
        MapTileData data = new MapTileData(pixmap.getWidth(), pixmap.getHeight());

        data.position(0, 0);
        TileDataMarker marker = data.getMarker();

        for(int x = 0; x < data.width(); x ++){
            for(int y = 0; y < data.height(); y ++){
                BlockPair pair = ColorMapper.get(pixmap.getPixel(y, pixmap.getWidth() - 1 - x));

                if(pair == null){
                    marker.floor = (byte)Blocks.stone.id;
                    marker.wall = (byte)Blocks.air.id;
                }else{
                    marker.floor = (byte)pair.floor.id;
                    marker.wall = (byte)pair.wall.id;
                }

                data.write();
            }
        }

        return data;
    }

    public static void writeMap(FileHandle file, ObjectMap<String, String> tags, MapTileData data) throws IOException{
        MapMeta meta = new MapMeta(version, tags, data.width(), data.height(), defaultBlockMap);

        DataOutputStream ds = new DataOutputStream(file.write(false));

        writeMapMeta(ds, meta);
        ds.write(data.toArray());

        ds.close();
    }

    /**Reads tile data, skipping meta.*/
    public static MapTileData readTileData(DataInputStream stream, boolean readOnly) throws IOException {
        MapMeta meta = readMapMeta(stream);
        return readTileData(stream, meta, readOnly);
    }

    /**Does not skip meta. Call after reading meta.*/
    public static MapTileData readTileData(DataInputStream stream, MapMeta meta, boolean readOnly) throws IOException {
        byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        return new MapTileData(bytes, meta.width, meta.height, meta.blockMap, readOnly);
    }

    /**Reads tile data, skipping meta tags.*/
    public static MapTileData readTileData(Map map, boolean readOnly){
        try {
            InputStream stream;

            if (!map.custom) {
                stream = Gdx.files.local("maps/" + map.name + "." + mapExtension).read();
            } else {
                stream = customMapDirectory.child(map.name + "." + mapExtension).read();
            }

            DataInputStream ds = new DataInputStream(stream);
            MapTileData data = MapIO.readTileData(ds, readOnly);
            ds.close();
            return data;
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static MapMeta readMapMeta(DataInputStream stream) throws IOException{
        ObjectMap<String, String> tags = new ObjectMap<>();
        IntIntMap map = new IntIntMap();

        int version = stream.readInt();

        byte tagAmount = stream.readByte();

        for(int i = 0; i < tagAmount; i ++){
            String name = stream.readUTF();
            String value = stream.readUTF();
            tags.put(name, value);
        }

        short blocks = stream.readShort();
        for(int i = 0; i < blocks; i ++){
            short id = stream.readShort();
            String name = stream.readUTF();
            Block block = Block.getByName(name);
            if(block == null){
                //Log.info("Map load info: No block with name {0} found.", name);
                block = Blocks.air;
            }
            map.put(id, block.id);
        }

        int width = stream.readShort();
        int height = stream.readShort();

        return new MapMeta(version, tags, width, height, map);
    }

    public static void writeMapMeta(DataOutputStream stream, MapMeta meta) throws IOException{
        stream.writeInt(meta.version);
        stream.writeByte((byte)meta.tags.size);

        for(Entry<String, String> entry : meta.tags.entries()){
            stream.writeUTF(entry.key);
            stream.writeUTF(entry.value);
        }

        stream.writeShort(Block.getAllBlocks().size);
        for(Block block : Block.getAllBlocks()){
            stream.writeShort(block.id);
            stream.writeUTF(block.name);
        }

        stream.writeShort(meta.width);
        stream.writeShort(meta.height);
    }
}