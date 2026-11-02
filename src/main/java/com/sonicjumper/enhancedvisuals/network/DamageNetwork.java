package com.sonicjumper.enhancedvisuals.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.sonicjumper.enhancedvisuals.Base;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.DamageSource;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Optional server assistance. Clients without this channel never receive our packets. */
public final class DamageNetwork {
    private static final String CHANNEL = "EVLegacy";
    private static final Set<NetworkManager> PEERS = ConcurrentHashMap.newKeySet();
    private static SimpleNetworkWrapper network;

    public static void init() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        network.registerMessage(Handler.class, Damage.class, 0, Side.CLIENT);
        DamageNetwork events = new DamageNetwork();
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(events);
    }

    public static boolean supported(NetworkManager manager) { return PEERS.contains(manager); }

    @SubscribeEvent
    public void registration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (!event.registrations.contains(CHANNEL)) return;
        if ("REGISTER".equals(event.operation)) PEERS.add(event.manager);
        else PEERS.remove(event.manager);
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) { PEERS.remove(event.manager); }
    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { PEERS.remove(event.manager); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || event.ammount <= 0) return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        if (!supported(player.playerNetServerHandler.netManager)) return;
        // The client picks the marks; send it what hit and with which item.
        Entity direct = event.source.getSourceOfDamage();
        ItemStack held = direct instanceof EntityLivingBase ? ((EntityLivingBase) direct).getHeldItem() : null;
        network.sendTo(new Damage(player.dimension, event.source.getDamageType(), event.ammount, flags(event.source),
            entityName(direct), entityName(event.source.getEntity()), itemName(held), weaponGroups(held)), player);
    }

    public static final int PROJECTILE = 1, FIRE = 2, EXPLOSION = 4, MAGIC = 8, UNBLOCKABLE = 16, ABSOLUTE = 32;
    public static final String[] FLAG_NAMES = { "projectile", "fire", "explosion", "magic", "unblockable", "absolute" };

    private static int flags(DamageSource source) {
        return (source.isProjectile() ? PROJECTILE : 0) | (source.isFireDamage() ? FIRE : 0) | (source.isExplosion() ? EXPLOSION : 0)
            | (source.isMagicDamage() ? MAGIC : 0) | (source.isUnblockable() ? UNBLOCKABLE : 0) | (source.isDamageAbsolute() ? ABSOLUTE : 0);
    }

    public static String entityName(Entity entity) {
        if (entity == null) return "";
        if (entity instanceof EntityPlayer) return "Player";
        String name = EntityList.getEntityString(entity);
        return name != null ? name : entity.getClass().getName();
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null ? name.toString() : stack.getItem().getClass().getName();
    }

    /** Uses the real stack, so modded tools that only declare a tool class still count. */
    private static String weaponGroups(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        Item item = stack.getItem();
        Set<String> tools = item.getToolClasses(stack);
        List<String> groups = new ArrayList<>();
        if (item instanceof ItemSword) groups.add("sword");
        if (item instanceof ItemAxe || tools.contains("axe")) groups.add("axe");
        if (item instanceof ItemPickaxe || tools.contains("pickaxe")) groups.add("pickaxe");
        if (item instanceof ItemSpade || tools.contains("shovel")) groups.add("shovel");
        if (item instanceof ItemHoe) groups.add("hoe");
        if (item instanceof ItemBow) groups.add("bow");
        if (item == Items.arrow) groups.add("arrow");
        return String.join(",", groups);
    }

    public static final class Damage implements IMessage {
        public int dimension;
        public String type, entity, attacker, item, groups;
        public float amount;
        public int flags;
        public Damage() {}
        public Damage(int dimension, String type, float amount, int flags, String entity, String attacker, String item, String groups) {
            this.dimension = dimension;
            this.type = type;
            this.amount = amount;
            this.flags = flags;
            this.entity = entity;
            this.attacker = attacker;
            this.item = item;
            this.groups = groups;
        }
        public boolean has(int flag) { return (flags & flag) != 0; }
        @Override public void fromBytes(ByteBuf buf) {
            dimension = buf.readInt();
            type = ByteBufUtils.readUTF8String(buf);
            amount = buf.readFloat();
            flags = buf.readUnsignedByte();
            entity = ByteBufUtils.readUTF8String(buf);
            attacker = ByteBufUtils.readUTF8String(buf);
            item = ByteBufUtils.readUTF8String(buf);
            groups = ByteBufUtils.readUTF8String(buf);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            ByteBufUtils.writeUTF8String(buf, type);
            buf.writeFloat(amount);
            buf.writeByte(flags);
            ByteBufUtils.writeUTF8String(buf, entity);
            ByteBufUtils.writeUTF8String(buf, attacker);
            ByteBufUtils.writeUTF8String(buf, item);
            ByteBufUtils.writeUTF8String(buf, groups);
        }
    }

    public static final class Handler implements IMessageHandler<Damage, IMessage> {
        @Override public IMessage onMessage(Damage message, MessageContext context) {
            Base.proxy.damage(message, context);
            return null;
        }
    }
}
