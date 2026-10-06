package net.tfminecraft.cooking.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.tfminecraft.cooking.Cooking;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.cooking.item.tag.TagStep;
import net.tfminecraft.cooking.item.tag.TagTrack;
import net.tfminecraft.cooking.loader.TrackLoader;
import net.tfminecraft.cooking.utils.QualityUtils;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class QualityRuntimeCoverageTest {
    Cooking plugin;
    List<TagTrack> tracks;
    @BeforeEach void setup() {
        MockBukkit.mock(); plugin=Cooking.plugin; Cooking.plugin=mock(Cooking.class);
        when(Cooking.plugin.getName()).thenReturn("Cooking"); when(Cooking.plugin.namespace()).thenReturn("cooking");
        tracks=new ArrayList<>(TrackLoader.oList); TrackLoader.oList.clear();
        QualityConfig.apply(2,2,null,null); PermissionEffectsConfig.apply(null,null); configure(false,true,1,1,true);
    }
    @AfterEach void cleanup() {
        TrackLoader.oList.clear(); TrackLoader.oList.addAll(tracks);
        QualityConfig.apply(1,5,null,null); PermissionEffectsConfig.apply(null,null); configure(true,true,.12,.15,true);
        MockBukkit.unmock(); Cooking.plugin=plugin;
    }

    @Test void partitionsPreserveRolesAndMainQualityWhileApplyingFreshnessAndLineage() {
        FoodItem beef=food("beef","meat",4), pork=food("pork","meat",2), salt=food("salt","salt",4);
        FoodItem plate=food("plate","neutral",5);
        CompositionConfig.apply(false,null,null,java.util.Set.of("neutral"),null,false,0,0,true);
        beef.addOrModifyTrack(track("freshness",true,10)); pork.addOrModifyTrack(track("freshness",true,20));
        beef.addOrModifyTrack(track("cooked",false,5)); beef.addOrModifyTrack(track("rising",true,5));
        CompositionResult result=CompositionQualityResolver.compose(null,Arrays.asList(beef,null,pork,salt,plate),CompositionContext.CARVE);
        assertEquals(3,result.getBaselineQuality()); assertEquals(3,result.getFinalQuality());
        assertEquals(List.of(beef,pork),result.getMains()); assertEquals(List.of(salt),result.getExtras()); assertEquals(List.of(plate),result.getNeutral());
        assertEquals(Map.of("freshness",15),result.getFreshnessTracks()); assertNotNull(result.getLineage());
        assertTrue(CompositionQualityResolver.partitionByRole(null,null).getMains().isEmpty());
        assertEquals(2,CompositionQualityResolver.resolveMainsQuality(null)); assertEquals(2,CompositionQualityResolver.resolveMainsQuality(List.of()));
        assertTrue(CompositionQualityResolver.resolveMainsFreshness(null).isEmpty());
        assertEquals(3,CompositionQualityResolver.resolve(null,List.of(beef,pork),null));
        FoodItem output=food("meal","meat",1); TrackLoader.oList.add(track("freshness",true,0));
        CompositionApplier.apply(output,result); assertEquals(3,output.getQualityMin()); assertEquals(15,output.getTagTrack("freshness").getValue());
        CompositionApplier.apply(null,result); CompositionApplier.apply(output,null);
        CompositionFreshnessApplier.applyTracks(null,Map.of("freshness",3)); CompositionFreshnessApplier.applyTracks(output,null);
        CompositionFreshnessApplier.applyTracks(output,Map.of()); CompositionFreshnessApplier.applyTracks(output,Map.of("missing",3));
        CompositionResult empty=new CompositionResult(1,2,null,null,null,null,null);
        assertTrue(empty.getMains().isEmpty()); assertTrue(empty.getExtras().isEmpty()); assertTrue(empty.getNeutral().isEmpty());
    }

    @Test void extrasUseQualityGapsCraftBonusesAndAgePenalties() {
        assertEquals(3,CompositionQualityResolver.applyExtras(3,null)); assertEquals(3,CompositionQualityResolver.applyExtras(3,List.of()));
        FoodItem high=food("high","salt",4), low=food("low","salt",1), same=food("same","salt",3);
        high.addOrModifyTrack(new TagTrack("craft",false,List.of(new TagStep("boost","Boost",0,1,1,0,.5))));
        high.addOrModifyTrack(new TagTrack("empty",true,List.of()));
        assertEquals(4,CompositionQualityResolver.applyExtras(3,List.of(high)));
        assertEquals(2,CompositionQualityResolver.applyExtras(3,List.of(low)));
        assertEquals(3,CompositionQualityResolver.applyExtras(3,List.of(same)));
        same.addOrModifyTrack(new TagTrack("aged",true,List.of(new TagStep("stale","Stale",0,1,1,1,0))));
        assertEquals(2,CompositionQualityResolver.applyExtras(3,List.of(same)));
        configure(false,true,0,0,false);
        assertEquals(3,CompositionQualityResolver.applyExtras(3,List.of(high,low)));
    }

    @Test void chefBoostsUseTheBestPermittedChanceAndRespectContexts() {
        Player chef=mock(Player.class); when(chef.hasPermission("chef")).thenReturn(true);
        PermissionEffectsConfig.apply(null,List.of(new PermissionEffectsConfig.CompositionEffect("none","other",1,4),
                new PermissionEffectsConfig.CompositionEffect("weak","chef",.5,1),new PermissionEffectsConfig.CompositionEffect("best","chef",1,2),
                new PermissionEffectsConfig.CompositionEffect("last","chef",.3,4)));
        FoodItem input=food("beef","meat",2);
        assertEquals(4,CompositionQualityResolver.compose(chef,List.of(input),CompositionContext.CARVE).getFinalQuality());
        assertEquals(2,CompositionQualityResolver.compose(chef,List.of(input),CompositionContext.MILLING).getFinalQuality());
        assertEquals(2,CompositionQualityResolver.compose(chef,List.of(input),null).getFinalQuality());
        PermissionEffectsConfig.apply(null,List.of(new PermissionEffectsConfig.CompositionEffect("zero","chef",0,4)));
        assertEquals(2,CompositionQualityResolver.compose(chef,List.of(input),CompositionContext.CARVE).getFinalQuality());
        PermissionEffectsConfig.apply(null,null); assertEquals(2,CompositionQualityResolver.compose(chef,List.of(input),CompositionContext.CARVE).getFinalQuality());
        PermissionEffectsConfig.apply(null,List.of(new PermissionEffectsConfig.CompositionEffect("chance","chef",.5,2)));
        ThreadLocalRandom random=mock(ThreadLocalRandom.class); when(random.nextDouble()).thenReturn(.9);
        try(var randomness=mockStatic(ThreadLocalRandom.class)) {
            randomness.when(ThreadLocalRandom::current).thenReturn(random);
            assertEquals(2,CompositionQualityResolver.compose(chef,List.of(input),CompositionContext.CARVE).getFinalQuality());
        }
    }

    @Test void legacyCompositionFiltersExcludedExtrasAndSupportsChefBoosts() {
        configure(true,true,0,0,true); Player chef=mock(Player.class); when(chef.hasPermission("chef")).thenReturn(true);
        FoodItem main=food("meat","meat",4), extra=food("salt","salt",1);
        assertEquals(4,CompositionQualityResolver.resolve(null,Arrays.asList(main,null,extra),CompositionContext.CARVE));
        assertEquals(3,CompositionQualityResolver.resolve(null,List.of(main,extra),null));
        assertEquals(2,CompositionQualityResolver.resolve(null,null,null));
        PermissionEffectsConfig.apply(null,List.of(new PermissionEffectsConfig.CompositionEffect("chef","chef",1,1)));
        assertEquals(5,CompositionQualityResolver.resolve(chef,List.of(main),CompositionContext.CARVE));
    }

    @Test void pickupPermissionsTakeTheLargestMinimumAndBias() {
        Player player=mock(Player.class); when(player.hasPermission("pickup")).thenReturn(true);
        PermissionEffectsConfig.apply(List.of(new PermissionEffectsConfig.PickupEffect("other","other",5,5),
                new PermissionEffectsConfig.PickupEffect("first","pickup",4,1),new PermissionEffectsConfig.PickupEffect("weaker","pickup",3,0)),null);
        assertEquals(4,OriginQualityResolver.resolve(player,food("beef","meat",1)));
        assertEquals(2,OriginQualityResolver.resolve(null,null));
        assertEquals(4,OriginQualityResolver.applyPickupPermissions(player,1));
        assertEquals(5,OriginQualityResolver.adjust(4,1,2)); assertEquals(1,OriginQualityResolver.adjust(-5,1,0));
    }

    @Test void qualityAndFreshnessArithmeticDoesNotOverflow() {
        assertEquals(5,QualityUtils.average(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(5,OriginQualityResolver.adjust(4,1,Integer.MAX_VALUE));
        FoodItem first=food("first","meat",3), second=food("second","meat",3);
        first.addOrModifyTrack(track("freshness",true,Integer.MAX_VALUE-1)); second.addOrModifyTrack(track("freshness",true,Integer.MAX_VALUE-1));
        assertEquals(Integer.MAX_VALUE-1,CompositionQualityResolver.resolveMainsFreshness(List.of(first,second)).get("freshness"));
    }

    @Test void configuredPickupQualityStaysInsideTheSupportedStarRange() {
        QualityConfig.apply(Integer.MAX_VALUE,Integer.MAX_VALUE,null,null);
        assertEquals(5,QualityConfig.getPickupMin()); assertEquals(5,QualityConfig.getPickupMax());
        assertEquals(5,OriginQualityResolver.resolve(null,null));
    }

    @Test void extremeChefBoostsClampToFiveStars() {
        Player player=mock(Player.class); when(player.hasPermission("chef")).thenReturn(true);
        PermissionEffectsConfig.apply(null,List.of(new PermissionEffectsConfig.CompositionEffect("chef","chef",1,Integer.MAX_VALUE)));
        assertEquals(5,CompositionQualityResolver.compose(player,List.of(food("beef","meat",4)),CompositionContext.CARVE).getFinalQuality());
    }

    private static void configure(boolean legacy,boolean modifiers,double up,double down,boolean craft) {
        CompositionConfig.apply(legacy,null,null,null,null,modifiers,up,down,craft);
    }
    private FoodItem food(String id,String category,int quality) {
        FoodItem item=new FoodItem(id,new MemoryConfiguration()); item.setCategory(category); item.setQualityRange(quality,quality); return item;
    }
    private TagTrack track(String id,boolean ageable,int value) {
        TagTrack track=new TagTrack(id,ageable,List.of(new TagStep("fresh","Fresh",Integer.MAX_VALUE,1,1,0,0)));
        track.forceSetValue(value); return track;
    }
}
