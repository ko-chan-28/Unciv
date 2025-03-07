package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.IconCircleGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.min

class Minimap(val mapHolder: WorldMapHolder) : Table(){
    private val allTiles = Group()
    private val tileImages = HashMap<TileInfo, Image>()
    private val scrollPositionIndicator = ImageGetter.getImage("OtherIcons/Camera")

    init {
        isTransform = false // don't try to resize rotate etc - this table has a LOT of children so that's valuable render time!

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        fun hexRow(vector2: Vector2) = vector2.x + vector2.y
        val maxHexRow = mapHolder.tileMap.values.asSequence().map { hexRow(it.position) }.max()!!
        val minHexRow = mapHolder.tileMap.values.asSequence().map { hexRow(it.position) }.min()!!
        val totalHexRows = maxHexRow - minHexRow

        for (tileInfo in mapHolder.tileMap.values) {
            val hex = ImageGetter.getImage("OtherIcons/Hexagon")

            val positionalVector = HexMath.hex2WorldCoords(tileInfo.position)


            val groupSize = 400f / totalHexRows
            hex.setSize(groupSize, groupSize)
            hex.setPosition(positionalVector.x * 0.5f * groupSize,
                    positionalVector.y * 0.5f * groupSize)
            hex.onClick {
                mapHolder.setCenterPosition(tileInfo.position)
            }
            allTiles.addActor(hex)
            tileImages[tileInfo] = hex

            topX = max(topX, hex.x + groupSize)
            topY = max(topY, hex.y + groupSize)
            bottomX = min(bottomX, hex.x)
            bottomY = min(bottomY, hex.y)
        }

        for (group in allTiles.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX, topY - bottomY)

        scrollPositionIndicator.touchable = Touchable.disabled
        allTiles.addActor(scrollPositionIndicator)

        add(allTiles)
        layout()
    }

    fun updateScrollPosition(scrollPos: Vector2, scale: Vector2){

        val scrollPositionIndicatorBaseScale = Vector2(allTiles.width / mapHolder.maxX, allTiles.height / mapHolder.maxY)

        scrollPositionIndicator.scaleX = scrollPositionIndicatorBaseScale.x * 10f * max(2f - scale.x, 0.25f)
        scrollPositionIndicator.scaleY = scrollPositionIndicatorBaseScale.y * 10f * max(2f - scale.y, 0.25f)

        val scrollPositionIndicatorOffset = Vector2(-50f * scrollPositionIndicator.scaleX, 125f + (50f * (1-scrollPositionIndicator.scaleY)))

        val scrollPosOnMinimap = Vector2((scrollPos.x / mapHolder.maxX) * allTiles.width, (scrollPos.y / mapHolder.maxY) * allTiles.height)
        scrollPosOnMinimap.x = MathUtils.clamp(scrollPosOnMinimap.x, -scrollPositionIndicatorOffset.x, allTiles.width + scrollPositionIndicatorOffset.x)
        scrollPosOnMinimap.y = MathUtils.clamp(scrollPosOnMinimap.y, -scrollPositionIndicatorOffset.x, scrollPositionIndicatorOffset.y)
        scrollPositionIndicator.setPosition(scrollPositionIndicatorOffset.x + scrollPosOnMinimap.x, scrollPositionIndicatorOffset.y - scrollPosOnMinimap.y)
    }

    private class CivAndImage(val civInfo: CivilizationInfo, val image: IconCircleGroup)
    private val cityIcons = HashMap<TileInfo, CivAndImage>()

    fun update(cloneCivilization: CivilizationInfo) {
        for((tileInfo, hex) in tileImages) {
            hex.color = when {
                !(UncivGame.Current.viewEntireMapForDebug || cloneCivilization.exploredTiles.contains(tileInfo.position)) -> Color.DARK_GRAY
                tileInfo.isCityCenter() && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getInnerColor()
                tileInfo.getCity() != null && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getOuterColor()
                else -> tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
            }

            if (tileInfo.isCityCenter() && cloneCivilization.exploredTiles.contains(tileInfo.position)
                    && (!cityIcons.containsKey(tileInfo) || cityIcons[tileInfo]!!.civInfo != tileInfo.getOwner())) {
                if (cityIcons.containsKey(tileInfo)) cityIcons[tileInfo]!!.image.remove() // city changed hands - remove old icon
                val nationIcon= ImageGetter.getNationIndicator(tileInfo.getOwner()!!.nation,hex.width * 3)
                nationIcon.setPosition(hex.x - nationIcon.width/3,hex.y - nationIcon.height/3)
                nationIcon.onClick {
                    mapHolder.setCenterPosition(tileInfo.position)
                }
                allTiles.addActor(nationIcon)
                cityIcons[tileInfo] = CivAndImage(tileInfo.getOwner()!!, nationIcon)
            }
        }
    }
}

class MinimapHolder(mapHolder: WorldMapHolder): Table() {
    val minimap = Minimap(mapHolder)
    val worldScreen = mapHolder.worldScreen
    private data class ToggleButtonInfo(val actor: Actor, val getSetting: ()->Boolean, val setSetting: (Boolean)->Unit)
    private val toggleButtonInfo: EnumMap<MinimapToggleButtons,ToggleButtonInfo> = EnumMap<MinimapToggleButtons,ToggleButtonInfo>(MinimapToggleButtons::class.java)

    init {
        add(getToggleIcons()).align(Align.bottom)
        add(getWrappedMinimap())
        pack()
    }

    enum class MinimapToggleButtons(val icon: String) {
        YIELD ("Food"),
        WORKED ("Population"),
        RESOURCES ("ResourceIcons/Cattle");
    }

    // "Api" when external code wants to toggle something together with our buttons
    private fun getButtonState(button: MinimapToggleButtons): Boolean {
        val info = toggleButtonInfo[button] ?: return false
        return info.getSetting()
    }
    private fun setButtonState(button: MinimapToggleButtons, value: Boolean) {
        val info = toggleButtonInfo[button] ?: return
        info.setSetting(value)
        info.actor.color.a = if (value) 1f else 0.5f
        worldScreen.shouldUpdate = true
    }
    private fun syncButtonState(button: MinimapToggleButtons) = setButtonState(button,getButtonState(button))
    internal fun syncButtonStates() {
        MinimapToggleButtons.values().forEach { syncButtonState(it) }
    }
    private fun toggleButtonState(button: MinimapToggleButtons) = setButtonState(button,!getButtonState(button))

    private fun addToggleButton(table:Table, button: MinimapToggleButtons) {
        val image =
                if ('/' in button.icon) {
                    ImageGetter.getImage(button.icon)
                        .surroundWithCircle(30f).apply { circle.color = Color.GREEN }
                        .surroundWithCircle(40f, false)
                } else {
                    ImageGetter.getStatIcon(button.icon).surroundWithCircle(40f)
                }
        image.apply { circle.color = Color.BLACK }
        toggleButtonInfo[button] = with(UncivGame.Current.settings) {
            when (button) {
                MinimapToggleButtons.YIELD -> ToggleButtonInfo(image, {showTileYields}, {showTileYields = it})
                MinimapToggleButtons.WORKED -> ToggleButtonInfo(image, {showWorkedTiles}, {showWorkedTiles = it})
                else -> ToggleButtonInfo(image, {showResourcesAndImprovements}, {showResourcesAndImprovements = it})
            }
        }
        syncButtonState(button)
        image.onClick {
            toggleButtonState(button)
        }
        table.add(image).row()
    }

    private fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()
        internalMinimapWrapper.add(minimap)

        internalMinimapWrapper.background = ImageGetter.getBackground(Color.GRAY)
        internalMinimapWrapper.pack()

        val externalMinimapWrapper = Table()
        externalMinimapWrapper.add(internalMinimapWrapper).pad(5f)
        externalMinimapWrapper.background = ImageGetter.getBackground(Color.WHITE)
        externalMinimapWrapper.pack()

        return externalMinimapWrapper
    }

    private fun getToggleIcons(): Table {
        val toggleIconTable = Table()
        MinimapToggleButtons.values().forEach {
            addToggleButton(toggleIconTable, it)
        }
        toggleIconTable.pack()
        return toggleIconTable
    }

    fun update(civInfo: CivilizationInfo) {
        isVisible = UncivGame.Current.settings.showMinimap
        minimap.update(civInfo)
    }


    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }
}
