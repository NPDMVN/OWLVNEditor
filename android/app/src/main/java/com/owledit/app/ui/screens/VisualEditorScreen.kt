package com.owledit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owledit.app.ui.MainUiState
import com.owledit.app.ui.components.*
import com.owledit.app.ui.i18n.LocalIsEnglish
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*
import com.owledit.core.model.AsuraStatStatus
import com.owledit.core.model.CurrencyStatus
import com.owledit.core.model.HeroStatus
import com.owledit.core.model.OwlCatalog
import com.owledit.core.model.UnlockOverview

@Composable
fun VisualEditorScreen(
    state: MainUiState,
    onOpenRoot: () -> Unit,
    onOpenLocalXml: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onSetCurrency: (String, Int) -> Unit,
    onUpdateHero: (
        bodyName: String,
        unlocked: Boolean?,
        level: Int?,
        secondSkill: Boolean?,
        unlockAllSkills: Boolean,
        unlockAllStyles: Boolean,
    ) -> Unit,
    onSetAsuraStat: (Int, Int) -> Unit,
    onSetMenuSkin: (Int, Boolean) -> Unit,
    onInspectContainer: (String) -> Unit,
    onSetQuickResource: (containerKey: String, entryKey: String, value: Int) -> Unit,
    onSetDifficultyUnlocked: (Int) -> Unit,
    onSetFeatureFlag: (containerKey: String?, key: String, enabled: Boolean) -> Unit,
) {
    val dash = state.dashboard
    if (dash == null) {
        EmptySessionCard(
            rootGranted = state.env.rootGranted,
            selectedPackage = state.settings.selectedPackage,
            onOpenRoot = onOpenRoot,
            onOpenLocalXml = onOpenLocalXml,
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        tr("Preset 1 Chạm", "Quick Presets"),
        tr("Mở Khóa (v3.8)", "Unlockables"),
        tr("Tiền Tệ & Vé", "Currencies"),
        tr("Tướng (${dash.heroes.size})", "Heroes (${dash.heroes.size})"),
        tr("Tu La & Skin", "Asura & Skins"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> PresetsTab(state, onApplyPreset)
            1 -> UnlockablesTab(
                overview = dash.unlockOverview,
                onApplyPreset = onApplyPreset,
                onInspectContainer = onInspectContainer,
                onSetQuickResource = onSetQuickResource,
                onSetDifficultyUnlocked = onSetDifficultyUnlocked,
                onSetFeatureFlag = onSetFeatureFlag,
            )
            2 -> CurrenciesTab(dash.currencies, state.catalog, onSetCurrency)
            3 -> HeroesTab(dash.heroes, onUpdateHero, onApplyPreset)
            4 -> AsuraAndSkinsTab(
                state = state,
                onSetAsuraStat = onSetAsuraStat,
                onSetMenuSkin = onSetMenuSkin,
                onApplyPreset = onApplyPreset,
                onInspectContainer = onInspectContainer,
            )
        }
    }
}

@Composable
private fun PresetsTab(
    state: MainUiState,
    onApplyPreset: (String) -> Unit,
) {
    val isEn = LocalIsEnglish.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle(
                title = tr(
                    "Gói Chỉnh Nhanh An Toàn (${state.presets.quickPresets.size} Presets v3.8.0)",
                    "Safe 1-Tap Presets (${state.presets.quickPresets.size} Presets v3.8.0)",
                ),
                subtitle = tr(
                    "Mọi tài nguyên được hiệu chỉnh đúng ngưỡng an toàn từ GameLogic.dll, tự đồng bộ khóa XOR & định dạng NRBF BinaryFormatter.",
                    "Every resource is calibrated to natural GameLogic.dll safe caps, automatically syncing XOR anti-tamper keys & MS-NRBF BinaryFormatter.",
                ),
            )
        }
        items(state.presets.quickPresets, key = { it.id }) { preset ->
            val isBundle = preset.id == "full_safe_bundle"
            val displayTitle = if (isEn && preset.titleEn.isNotBlank()) preset.titleEn else preset.title
            val displayDesc = if (isEn && preset.descriptionEn.isNotBlank()) preset.descriptionEn else preset.description
            val displayBadge = if (isEn && preset.badgeEn.isNotBlank()) preset.badgeEn else preset.badge

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBundle) OwlRubyContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    1.dp,
                    if (isBundle) OwlRuby else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (displayBadge.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            StatusPill(
                                text = displayBadge,
                                containerColor = if (isBundle) OwlRuby else OwlSapphireContainer,
                                contentColor = if (isBundle) OwlTextPrimary else OwlSapphire,
                            )
                        }
                    }
                    Text(
                        text = displayDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { onApplyPreset(preset.id) }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Áp Dụng", "Apply"))
                        }
                    }
                }
            }
        }
    }
}

private data class UnlockCategoryRow(
    val id: String,
    val title: String,
    val detail: String,
    val unlocked: Int,
    val total: Int,
    val presetId: String,
    val presetLabel: String,
    val containerKey: String,
)

@Composable
private fun UnlockablesTab(
    overview: UnlockOverview,
    onApplyPreset: (String) -> Unit,
    onInspectContainer: (String) -> Unit,
    onSetQuickResource: (containerKey: String, entryKey: String, value: Int) -> Unit,
    onSetDifficultyUnlocked: (Int) -> Unit,
    onSetFeatureFlag: (containerKey: String?, key: String, enabled: Boolean) -> Unit,
) {
    val isEn = LocalIsEnglish.current
    val rows = remember(overview, isEn) {
        listOf(
            UnlockCategoryRow(
                id = "pets",
                title = tr(isEn, "Thú Cưng & Tự Động Nhặt (Pet Paradise)", "Pets & Auto-Pick Units (Pet Paradise)"),
                detail = tr(
                    isEn,
                    "Gồm TinyAsura, bản Vàng, Thân thiện 100, Độ no 100 & 8 Tự động nhặt",
                    "Includes TinyAsura, Gold variants, 100 Affinity/Satiety & 8 Auto-Pick units",
                ),
                unlocked = overview.petsUnlocked,
                total = overview.petsTotal,
                presetId = "unlock_pets_garden",
                presetLabel = tr(isEn, "Mở Full 32 Pet", "Unlock 32 Pets"),
                containerKey = "petParadise",
            ),
            UnlockCategoryRow(
                id = "naga",
                title = tr(isEn, "Thần Lực Naga / Chakra (8 Tướng Naga)", "Naga Hero Chakra (8 Naga Heroes)"),
                detail = tr(
                    isEn,
                    "40 nhánh Chakra (Xuanshe, Heyao, Aimi, Uliana, Annice, Ruthven, Erdene, Dolly)",
                    "40 Chakra branches (Xuanshe, Heyao, Aimi, Uliana, Annice, Ruthven, Erdene, Dolly)",
                ),
                unlocked = overview.nagaPowersMaxed,
                total = overview.nagaPowersTotal,
                presetId = "max_naga_six_realms",
                presetLabel = tr(isEn, "Max Lv.5 Naga", "Max Lv.5 Naga"),
                containerKey = "NagaPower",
            ),
            UnlockCategoryRow(
                id = "six_cycle",
                title = tr(isEn, "Sức Mạnh Lục Đạo Luân Hồi (SixCyclePower)", "Six Realms Powers (SixCyclePower)"),
                detail = tr(
                    isEn,
                    "37 nội tại Lục Đạo (Nhân/Thú/Quỷ/Địa Ngục/Tu La/Thiên Nhân Đạo)",
                    "37 Six Realms passives (Human / Animal / Ghost / Inferno / Asura / Deva)",
                ),
                unlocked = overview.sixCyclePowersUnlocked,
                total = overview.sixCyclePowersTotal,
                presetId = "max_naga_six_realms",
                presetLabel = tr(isEn, "Mở 37 Lục Đạo", "Unlock 37 Powers"),
                containerKey = "SixCyclePower",
            ),
            UnlockCategoryRow(
                id = "hero_skins",
                title = tr(isEn, "Skin Tướng v3.8.0 (HeroSkin)", "Hero Skins v3.8.0 (HeroSkin)"),
                detail = tr(
                    isEn,
                    "Toàn bộ 112 Skin Tướng (từ Quan Huying → Dolly/LionKnight/Yoiko)",
                    "All 112 Hero Skins (from Quan Huying through Dolly / LionKnight / Yoiko)",
                ),
                unlocked = overview.heroSkinsUnlocked,
                total = overview.heroSkinsTotal,
                presetId = "unlock_all_skins",
                presetLabel = tr(isEn, "Mở 112 Skin", "Unlock 112 Skins"),
                containerKey = "HeroSkin",
            ),
            UnlockCategoryRow(
                id = "skin_powers",
                title = tr(isEn, "Sức Mạnh Skin Đặc Biệt (HeroSkinPower)", "Special Skin Powers (HeroSkinPower)"),
                detail = tr(
                    isEn,
                    "23 hiệu ứng Skin Power đặc biệt (Ruthven God, Uliana Rock, Youzi God...)",
                    "23 special Skin Power effects (Ruthven God, Uliana Rock, Youzi God...)",
                ),
                unlocked = overview.heroSkinPowersUnlocked,
                total = overview.heroSkinPowersTotal,
                presetId = "unlock_all_skins",
                presetLabel = tr(isEn, "Mở 23 Power", "Unlock 23 Powers"),
                containerKey = "HeroSkinPower",
            ),
            UnlockCategoryRow(
                id = "npc_skins",
                title = tr(isEn, "Skin NPC Phòng Chờ (NPCSkin)", "Lobby NPC Skins (NPCSkin)"),
                detail = tr(
                    isEn,
                    "28 trang phục cho 5 NPC (Zhao, Ken, Mary, Zhimei, Asura)",
                    "28 outfits for all 5 lobby NPCs (Zhao, Ken, Mary, Zhimei, Asura)",
                ),
                unlocked = overview.npcSkinsUnlocked,
                total = overview.npcSkinsTotal,
                presetId = "unlock_all_skins",
                presetLabel = tr(isEn, "Mở 28 NPC", "Unlock 28 NPCs"),
                containerKey = "NPCSkin",
            ),
            UnlockCategoryRow(
                id = "statues",
                title = tr(isEn, "Tượng Phòng Thành Tựu (Statues)", "Achievement Room Statues"),
                detail = tr(
                    isEn,
                    "122 Tượng Boss, Quái & Tượng Kỷ Niệm 1–6 Năm",
                    "122 Boss, Monster & 1st–6th Anniversary Statues",
                ),
                unlocked = overview.statuesUnlocked,
                total = overview.statuesTotal,
                presetId = "unlock_statues_albums",
                presetLabel = tr(isEn, "Mở 122 Tượng", "Unlock 122 Statues"),
                containerKey = "AchievementRoomStatue",
            ),
            UnlockCategoryRow(
                id = "albums",
                title = tr(isEn, "Đĩa Nhạc BGM Phòng Chờ (Album)", "Lobby BGM Music Albums"),
                detail = tr(
                    isEn,
                    "42 Đĩa nhạc nền (Walkman / Máy nghe nhạc)",
                    "42 Lobby background music albums (Walkman)",
                ),
                unlocked = overview.albumsUnlocked,
                total = overview.albumsTotal,
                presetId = "unlock_statues_albums",
                presetLabel = tr(isEn, "Mở 42 Đĩa", "Unlock 42 Albums"),
                containerKey = "Album",
            ),
            UnlockCategoryRow(
                id = "items",
                title = tr(isEn, "Sổ Tay Vật Phẩm (Items v3.8.0)", "Item Catalog (Items v3.8.0)"),
                detail = tr(
                    isEn,
                    "401 Vật phẩm (bao gồm các bản WithTrait & CurseSeed)",
                    "401 Items (including WithTrait & CurseSeed variants)",
                ),
                unlocked = overview.itemsUnlocked,
                total = overview.itemsTotal,
                presetId = "unlock_all_items_bestiary",
                presetLabel = tr(isEn, "Mở 401 Item", "Unlock 401 Items"),
                containerKey = "illustrated_characterEffect",
            ),
            UnlockCategoryRow(
                id = "shared_skills",
                title = tr(isEn, "Kỹ Năng Boss Chia Sẻ (SharedSkill)", "Shared Boss Skills (SharedSkill)"),
                detail = tr(
                    isEn,
                    "14 Kỹ năng Boss chia sẻ & 14 Nguyên liệu rèn",
                    "14 Shared Boss Skills & 14 crafting materials",
                ),
                unlocked = overview.sharedSkillsUnlocked,
                total = overview.sharedSkillsTotal,
                presetId = "unlock_all_items_bestiary",
                presetLabel = tr(isEn, "Mở 14 Kỹ năng", "Unlock 14 Skills"),
                containerKey = "SharedSkill",
            ),
            UnlockCategoryRow(
                id = "mirage_pass",
                title = tr(isEn, "Chiến Lệnh Cao Cấp (Mirage Pass)", "Premium Mirage Pass"),
                detail = tr(
                    isEn,
                    "Tổng cấp tích lũy: ${overview.battlePassTotalLevel} • Giữ nguyên trạng thái chưa nhận để tự bấm nhận thưởng trong game",
                    "Total Pass Levels: ${overview.battlePassTotalLevel} • Keeps rewards claimable in-game",
                ),
                unlocked = overview.battlePassHighUnlocked,
                total = overview.battlePassSeasonsSeen.coerceAtLeast(1),
                presetId = "unlock_battlepass_lobby",
                presetLabel = tr(isEn, "Mở Mirage Pass", "Unlock Pass"),
                containerKey = "battlePass",
            ),
            UnlockCategoryRow(
                id = "ban_slots",
                title = tr(isEn, "Ô Cấm Vật Phẩm (Ban Slots)", "Item Ban Slots"),
                detail = tr(
                    isEn,
                    "31 Ô Cấm Vật Phẩm + 9 Bộ nhớ cấm",
                    "31 Item Ban Slots + 9 Ban Presets",
                ),
                unlocked = overview.banSlotsUnlocked,
                total = overview.banSlotsTotal,
                presetId = "unlock_battlepass_lobby",
                presetLabel = tr(isEn, "Mở 31 Ô Cấm", "Unlock 31 Slots"),
                containerKey = "LocatableBanSlotUnlockState",
            ),
            UnlockCategoryRow(
                id = "garden",
                title = tr(isEn, "Khu Vườn Trồng Cây (Garden)", "Garden & Flower Beds"),
                detail = tr(
                    isEn,
                    "Mở hệ thống Khu Vườn & đủ 9 Ô Đất Trồng",
                    "Unlocks Garden system & all 9 flower beds",
                ),
                unlocked = overview.gardenPlotsUnlocked,
                total = overview.gardenPlotsTotal,
                presetId = "unlock_pets_garden",
                presetLabel = tr(isEn, "Mở 9 Ô Vườn", "Unlock 9 Plots"),
                containerKey = "Garden",
            ),
            UnlockCategoryRow(
                id = "lobby",
                title = tr(isEn, "Công Trình & Tính Năng Phòng Chờ", "Lobby Facilities & Features"),
                detail = "BuildCat (${overview.buildProjectsUnlocked}/${overview.buildProjectsTotal}) • Travel (${overview.travelFeaturesUnlocked}/${overview.travelFeaturesTotal})",
                unlocked = overview.buildProjectsUnlocked + overview.travelFeaturesUnlocked,
                total = overview.buildProjectsTotal + overview.travelFeaturesTotal,
                presetId = "unlock_battlepass_lobby",
                presetLabel = tr(isEn, "Mở Full Sảnh", "Unlock Lobby"),
                containerKey = "Travel",
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle(
                title = tr(
                    "Toàn Bộ Tài Nguyên Mở Khóa (OWL v3.8.0 — Mức An Toàn)",
                    "All Unlockable Resources (OWL v3.8.0 — Safe Caps)",
                ),
                subtitle = tr(
                    "Theo dõi trực tiếp tiến độ từng hạng mục, mở khóa toàn bộ Thành tựu hoặc chuẩn hóa tài nguyên về ngưỡng an toàn chuẩn.",
                    "Track real-time unlock progress, unlock all 236+ Achievements, or normalize over-cap values down to natural safe caps.",
                ),
            )
        }

        // Safe-Cap Normalize Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OwlEmeraldContainer.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, OwlEmerald.copy(alpha = 0.65f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = OwlEmerald)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Chuẩn Hóa Về Mức An Toàn (Safe-Cap)", "Normalize To Safe Caps"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = tr(
                                "Hạ các chỉ số vượt ngưỡng (Đá Hồn ≤500k, Đá Lam/Đỏ ≤50k, Tu La ≤12k, Phòng Thành Tựu ≤7, Bank ≤200, Mảnh ≤500, NL ≤200).",
                                "Clamps over-cap stats (Soul Stones ≤500k, Sapphires/Rubies ≤50k, Asura ≤12k, Achievement Room ≤7, Bank ≤200, Fragments ≤500, Materials ≤200).",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { onApplyPreset("normalize_safe_caps") }) {
                        Text(tr("Chuẩn Hóa", "Normalize"))
                    }
                }
            }
        }

        // Achievements Highlight Card (236+ Achievements)
        item {
            val allAchDone = overview.achievementsCompleted >= overview.achievementsTotal && overview.achievementsTotal > 0
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.5.dp,
                    if (allAchDone) OwlEmerald else OwlRuby,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = OwlSoulGold)
                            Text(
                                text = tr("Thành Tựu (Achievements)", "Achievements (236+)"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        StatusPill(
                            text = "${overview.achievementsCompleted}/${overview.achievementsTotal}",
                            containerColor = if (allAchDone) OwlEmeraldContainer else OwlRubyContainer,
                            contentColor = if (allAchDone) OwlEmerald else OwlRuby,
                        )
                    }
                    Text(
                        text = tr(
                            "Đã nhận thưởng: ${overview.achievementsClaimed}/${overview.achievementsTotal} • Giữ nguyên ngày hoàn thành cũ của bạn. Chọn 'Mở Hết (Tự nhận quà)' để vào game bấm nút 'Có thể lấy được'!",
                            "Claimed: ${overview.achievementsClaimed}/${overview.achievementsTotal} • Preserves original completion dates. Choose 'Unlock (Claimable)' to claim rewards & points directly in-game!",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1.1f),
                            onClick = { onApplyPreset("unlock_all_achievements") },
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Mở Hết (Tự nhận)", "Unlock (Claimable)"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onApplyPreset("unlock_all_achievements_claimed") },
                        ) {
                            Text(tr("Đánh dấu đã nhận", "Mark All Claimed"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        items(rows, key = { it.id }) { row ->
            val done = row.unlocked >= row.total && row.total > 0
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (done) OwlEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            text = "${row.unlocked}/${row.total}",
                            containerColor = if (done) OwlEmeraldContainer else OwlSoulGoldContainer,
                            contentColor = if (done) OwlEmerald else OwlSoulGold,
                        )
                    }
                    Text(
                        text = row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(0.8f),
                            onClick = { onInspectContainer(row.containerKey) },
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Xem chi tiết", "Inspect"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            modifier = Modifier.weight(1.1f),
                            onClick = { onApplyPreset(row.presetId) },
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(row.presetLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Quick Numeric Resource Sliders/Inputs
        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle(
                title = tr("Tài Nguyên, Mảnh Đổi, Vé & Tiến Trình Nhanh", "Fragments, Tickets & Progress Counters"),
                subtitle = tr(
                    "Chỉnh trực tiếp các bộ đếm đặc biệt bên trong NRBF & PlayerArchive.",
                    "Edit special numeric counters directly inside NRBF & PlayerArchive containers.",
                ),
            )
        }

        item {
            QuickNumberEditorCard(
                title = tr("Mảnh Tướng Vạn Năng (HP_HeroCommonPiece)", "Universal Hero Fragments (HP_HeroCommonPiece)"),
                subtitle = tr("Đổi tướng trong Phòng chờ • Mức an toàn: 500 (Tối đa khuyến nghị: 999)", "Exchange heroes in Lobby • Safe cap: 500 (Recommended max: 999)"),
                currentValue = overview.heroCommonPieces,
                quickPreset = 500,
                onApply = { onSetQuickResource("exchangeablePieces", "HP_HeroCommonPiece", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Mảnh Skin Vạn Năng (SkinPiecesCount)", "Universal Skin Fragments (SkinPiecesCount)"),
                subtitle = tr("Đổi Skin tướng • Mức an toàn: 500 (Tối đa khuyến nghị: 999)", "Exchange hero skins • Safe cap: 500 (Recommended max: 999)"),
                currentValue = overview.skinCommonPieces,
                quickPreset = 500,
                onApply = { onSetQuickResource("exchangeablePieces", "SkinPiecesCount", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Vé Tẩy Ngọc Lục Đạo (OrbRefineTicketCount)", "Six Realms Orb Refine Tickets"),
                subtitle = tr("Container SixCycleOrb • Mức an toàn: 200", "Container SixCycleOrb • Safe cap: 200"),
                currentValue = overview.orbRefineTickets,
                quickPreset = 200,
                onApply = { onSetQuickResource("SixCycleOrb", "OrbRefineTicketCount", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Vé Giám Định Realmsoul (ESRIdentifyTicketCount)", "Realmsoul Identify Tickets"),
                subtitle = tr("Container GameData • Mức an toàn: 200", "Container GameData • Safe cap: 200"),
                currentValue = overview.esrIdentifyTickets,
                quickPreset = 200,
                onApply = { onSetQuickResource("GameData", "ESRIdentifyTicketCount", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Thức Ăn Thú Cưng (PP_FoodCount)", "Pet Food (PP_FoodCount)"),
                subtitle = tr("Container petParadise • Mức an toàn: 300", "Container petParadise • Safe cap: 300"),
                currentValue = overview.petFoodCount,
                quickPreset = 300,
                onApply = { onSetQuickResource("petParadise", "PP_FoodCount", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Đồ Chơi Thú Cưng (PP_ToyCount)", "Pet Toys (PP_ToyCount)"),
                subtitle = tr("Container petParadise • Mức an toàn: 300", "Container petParadise • Safe cap: 300"),
                currentValue = overview.petToyCount,
                quickPreset = 300,
                onApply = { onSetQuickResource("petParadise", "PP_ToyCount", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Xu Sự Kiện Ma Dạ (Moye_Token)", "Moye Event Tokens (Moye_Token)"),
                subtitle = tr("Container MiscArchive • Mức an toàn: 300", "Container MiscArchive • Safe cap: 300"),
                currentValue = overview.moyeTokenCount,
                quickPreset = 300,
                onApply = { onSetQuickResource("MiscArchive", "Moye_Token", it) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Cấp Phiêu Lưu (AdventureLevel)", "Adventure Level (Max 30)"),
                subtitle = tr("Tối đa 30 — Mở vật phẩm & công thức rượu theo mốc cấp", "Max 30 — Unlocks items & lobby drink recipes by level"),
                currentValue = overview.adventureLevel,
                quickPreset = 30,
                onApply = { onSetQuickResource("AdventureLevel", "AdventureLevelLevelArchiveKey", it.coerceIn(0, 30)) },
            )
        }
        item {
            QuickNumberEditorCard(
                title = tr("Cấp Độ Khó Mở Khóa (Real Mirage / Pandemonium)", "Unlocked Difficulty (Real Mirage / Pandemonium)"),
                subtitle = tr("Tối đa 14 (Mở khóa toàn bộ cấp độ khó)", "Max 14 (Unlocks all difficulty tiers)"),
                currentValue = overview.difficultyUnlocked,
                quickPreset = 14,
                onApply = onSetDifficultyUnlocked,
            )
        }

        // Quick Feature Flags
        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle(
                title = tr("Đặc Quyền & Chế Độ Mở Khóa", "Privileges & Game Mode Toggles"),
                subtitle = tr("Bật/tắt tức thì các cờ tính năng trong PlayerArchive & Config.", "Instantly toggle feature flags inside PlayerArchive & Config."),
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    FlagToggleRow(
                        title = tr("Thẻ VIP Quầy Bar (HasBuyBarVipCard)", "Lobby Bar VIP Card (HasBuyBarVipCard)"),
                        checked = overview.barVipUnlocked,
                        onCheckedChange = { onSetFeatureFlag(null, "HasBuyBarVipCard", it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    FlagToggleRow(
                        title = tr("Chế Độ Hỗn Độn Lục Đạo (SixCycleChaosUnlock)", "Six Realms Chaos Mode (SixCycleChaosUnlock)"),
                        checked = overview.chaosModeUnlocked,
                        onCheckedChange = { onSetFeatureFlag("Config", "SixCycleChaosUnlock", it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    FlagToggleRow(
                        title = tr("Chế Độ Imba (ImbaMode)", "Imba Mode (ExperimentalGameplayArchive)"),
                        checked = overview.imbaModeUnlocked,
                        onCheckedChange = { onSetFeatureFlag("Config", "ExperimentalGameplayArchive.ImbaMode.toggleOn", it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    FlagToggleRow(
                        title = tr("Ngân Hàng Phòng Chờ (Bank)", "Lobby Bank System (Bank)"),
                        checked = overview.bankUnlocked,
                        onCheckedChange = { onSetFeatureFlag("Bank", "systemUnlock", it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    FlagToggleRow(
                        title = tr("Cổng Chọn Màn Chơi (LevelPortal)", "Level Portal (LevelPortal)"),
                        checked = overview.levelPortalUnlocked,
                        onCheckedChange = { onSetFeatureFlag("LevelPortal", "LevelPortalSystemUnlock", it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickNumberEditorCard(
    title: String,
    subtitle: String,
    currentValue: Int,
    quickPreset: Int,
    onApply: (Int) -> Unit,
) {
    var draft by remember(title, currentValue) { mutableStateOf(currentValue.toString()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { s -> draft = s.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onApply(quickPreset) }) {
                    Text(formatNumber(quickPreset))
                }
                Button(
                    enabled = draft.toIntOrNull() != null && draft.toIntOrNull() != currentValue,
                    onClick = { draft.toIntOrNull()?.let(onApply) },
                ) {
                    Text(tr("Đặt", "Set"))
                }
            }
        }
    }
}

@Composable
private fun FlagToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CurrenciesTab(
    currencies: List<CurrencyStatus>,
    catalog: OwlCatalog,
    onSetCurrency: (String, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle(
                title = tr("Tiền Tệ, Vé & Chỉ Số Anti-Tamper (EncryptValue)", "Currencies, Tickets & Anti-Tamper Stats"),
                subtitle = tr(
                    "Mỗi lần sửa sẽ tự động sinh khóa XOR mới (200..499) cho cặp <Key> và <Key>Key giống 100% logic game.",
                    "Each edit automatically generates a fresh XOR key (200..499) for <Key> and <Key>Key matching exact game logic.",
                ),
            )
        }
        items(currencies, key = { it.key }) { item ->
            CurrencyRowCard(item, catalog, onSetCurrency)
        }
    }
}

@Composable
private fun CurrencyRowCard(
    item: CurrencyStatus,
    catalog: OwlCatalog,
    onSetCurrency: (String, Int) -> Unit,
) {
    val isEn = LocalIsEnglish.current
    var draft by remember(item.key, item.value) { mutableStateOf(item.value.toString()) }
    val parsed = draft.toIntOrNull()
    val overSafeMax = (parsed ?: item.value) > item.safeMax
    val displayLabel = if (isEn) {
        catalog.currencies[item.key]?.en?.ifBlank { item.label } ?: item.label
    } else {
        item.label
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                overSafeMax -> OwlSoulGold
                item.modified -> OwlEmerald
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${item.key} • Safe-Max: ${formatNumber(item.safeMax)} • XOR key: ${item.encryptKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.modified) {
                    StatusPill(tr("Đã sửa", "Edited"), OwlEmeraldContainer, OwlEmerald)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { s -> draft = s.filter { it.isDigit() }.take(9) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = parsed != null && parsed != item.value,
                    onClick = { parsed?.let { onSetCurrency(item.key, it) } },
                ) {
                    Text(tr("Áp dụng", "Apply"))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val quickStep = when {
                    item.safeMax <= 15 -> 1
                    item.safeMax >= 100_000 -> 10_000
                    else -> 50
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    onClick = { onSetCurrency(item.key, (item.value + quickStep).coerceAtMost(item.safeMax)) },
                ) {
                    Text("+${formatNumber(quickStep)}", maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    onClick = { onSetCurrency(item.key, item.safeMax) },
                ) {
                    Text("Safe-Max", maxLines = 1)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    onClick = { onSetCurrency(item.key, 0) },
                ) {
                    Text(tr("Đặt 0", "Set 0"), maxLines = 1)
                }
            }

            if (overSafeMax) {
                Text(
                    text = tr(
                        "⚠ Giá trị vượt ngưỡng Safe-Max (${formatNumber(item.safeMax)}) — cân nhắc nếu dùng Cloud Save.",
                        "⚠ Value exceeds Safe-Max (${formatNumber(item.safeMax)}) — use caution with Cloud Save.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = OwlSoulGold,
                )
            }
        }
    }
}

@Composable
private fun HeroesTab(
    heroes: List<HeroStatus>,
    onUpdateHero: (String, Boolean?, Int?, Boolean?, Boolean, Boolean) -> Unit,
    onApplyPreset: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var onlyUnlocked by remember { mutableStateOf(false) }

    val filtered = remember(heroes, query, onlyUnlocked) {
        val q = query.trim().lowercase()
        heroes.filter { h ->
            (!onlyUnlocked || h.unlocked) && (
                q.isEmpty() ||
                    h.nameVi.lowercase().contains(q) ||
                    h.nameEn.lowercase().contains(q) ||
                    h.bodyName.lowercase().contains(q)
                )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(tr("Tìm tướng (Quan, Ginzo, Uliana...)", "Search hero (Quan, Ginzo, Uliana...)")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = onlyUnlocked,
                        onClick = { onlyUnlocked = !onlyUnlocked },
                        label = { Text(tr("Đã mở", "Unlocked")) },
                    )
                }
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onApplyPreset("unlock_all_heroes") },
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr(
                            "Mở Full ${heroes.size} Tướng Lv.15 Max + Kỹ Năng & Phong Cách",
                            "Unlock All ${heroes.size} Heroes Lv.15 Max + Skills & Styles",
                        )
                    )
                }
            }
        }

        items(filtered, key = { it.bodyName }) { hero ->
            HeroCard(hero, onUpdateHero)
        }
    }
}

@Composable
private fun HeroCard(
    hero: HeroStatus,
    onUpdateHero: (String, Boolean?, Int?, Boolean?, Boolean, Boolean) -> Unit,
) {
    val isEn = LocalIsEnglish.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (hero.unlocked) OwlSapphire.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEn) hero.nameEn else "${hero.nameVi} (${hero.nameEn})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tr(
                            "${hero.bodyName} • Lv.${hero.level}/15 • Kỹ năng: ${hero.skillsUnlocked}/${hero.skillsTotal} • Phong cách: ${hero.stylesUnlocked}/${hero.stylesTotal}",
                            "${hero.bodyName} • Lv.${hero.level}/15 • Skills: ${hero.skillsUnlocked}/${hero.skillsTotal} • Styles: ${hero.stylesUnlocked}/${hero.stylesTotal}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = hero.unlocked,
                    onCheckedChange = { checked ->
                        onUpdateHero(hero.bodyName, checked, null, null, false, false)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tr("Cấp:", "Level:"), style = MaterialTheme.typography.labelMedium)
                FilledTonalIconButton(
                    modifier = Modifier.size(32.dp),
                    enabled = hero.level > 1,
                    onClick = { onUpdateHero(hero.bodyName, null, (hero.level - 1).coerceAtLeast(1), null, false, false) },
                ) {
                    Icon(Icons.Default.Remove, contentDescription = tr("Giảm cấp", "Decrease level"), modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${hero.level}/15",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OwlSoulGold,
                )
                FilledTonalIconButton(
                    modifier = Modifier.size(32.dp),
                    enabled = hero.level < 15,
                    onClick = { onUpdateHero(hero.bodyName, null, (hero.level + 1).coerceAtMost(15), null, false, false) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = tr("Tăng cấp", "Increase level"), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = hero.secondSkillUnlocked,
                    onClick = {
                        onUpdateHero(hero.bodyName, null, null, !hero.secondSkillUnlocked, false, false)
                    },
                    label = { Text(tr("Kỹ năng phụ", "2nd Skill")) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateHero(hero.bodyName, true, 15, true, true, true) },
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Max Tướng Lv.15 + Full Skill/Style", "Max Hero Lv.15 + All Skills/Styles"))
                }
            }
        }
    }
}

@Composable
private fun AsuraAndSkinsTab(
    state: MainUiState,
    onSetAsuraStat: (Int, Int) -> Unit,
    onSetMenuSkin: (Int, Boolean) -> Unit,
    onApplyPreset: (String) -> Unit,
    onInspectContainer: (String) -> Unit,
) {
    val dash = state.dashboard ?: return
    val isEn = LocalIsEnglish.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        title = tr("Phân Bổ Sức Mạnh Tu La", "Power of Asura Allocation"),
                        subtitle = "NRBF EXAsuraPowerPointInvest & AsuraPowerConfig",
                    )
                }
                FilledTonalButton(onClick = { onApplyPreset("max_asura") }) {
                    Text(tr("Max Tu La", "Max Asura"))
                }
            }
        }

        items(dash.asuraStats, key = { "asura_${it.statId}" }) { stat ->
            AsuraStatCard(stat, onSetAsuraStat)
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        title = tr(
                            "Giao Diện Phòng Chờ (${dash.menuSkins.count { it.unlocked }}/${dash.menuSkins.size})",
                            "Lobby Menu Skins (${dash.menuSkins.count { it.unlocked }}/${dash.menuSkins.size})",
                        ),
                        subtitle = "NRBF MenuSkinUnlock (MenuSkinType)",
                    )
                }
                FilledTonalButton(onClick = { onApplyPreset("unlock_all_skins") }) {
                    Text(tr("Mở Full Skin", "Unlock All"))
                }
            }
        }

        items(dash.menuSkins, key = { "menu_${it.id}" }) { skin ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) skin.code else skin.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${skin.code} (#${skin.id})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = skin.unlocked,
                        onCheckedChange = { onSetMenuSkin(skin.id, it) },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle(
                title = tr(
                    "Kho Dữ Liệu NRBF Chính (Chạm để chỉnh chi tiết)",
                    "Main NRBF Containers (Tap to Inspect & Edit)",
                ),
                subtitle = tr(
                    "Mở trực tiếp bảng Key-Value đã giải mã từ BinaryFormatter.",
                    "Open decoded BinaryFormatter Key-Value tables directly.",
                ),
            )
        }

        val featuredContainers = dash.containers.filter { it.editable }.take(36)
        items(featuredContainers, key = { "cnt_${it.md5Key}" }) { c ->
            val catEntry = state.catalog.containers[c.plainKey]
            val containerTitle = if (isEn) {
                catEntry?.en?.ifBlank { c.plainKey } ?: c.plainKey
            } else {
                catEntry?.vi?.ifBlank { c.plainKey } ?: c.plainKey
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectContainer(c.md5Key) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = containerTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${c.plainKey} • ${c.kindLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(
                        text = tr("${c.entryCount} mục", "${c.entryCount} items"),
                        containerColor = OwlSapphireContainer,
                        contentColor = OwlSapphire,
                    )
                }
            }
        }
    }
}

private fun asuraStatLabelEn(code: String): String = when (code) {
    "Damage" -> "Attack Damage"
    "AttackSpeed" -> "Attack Speed"
    "Critical" -> "Crit Rate"
    "MaxHealth" -> "Max Health"
    "MoveSpeed" -> "Movement Speed"
    "Armor" -> "Armor"
    else -> code
}

@Composable
private fun AsuraStatCard(
    stat: AsuraStatStatus,
    onSetAsuraStat: (Int, Int) -> Unit,
) {
    val isEn = LocalIsEnglish.current
    var draft by remember(stat.statId, stat.value) { mutableStateOf(stat.value.toString()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = if (isEn) asuraStatLabelEn(stat.code) else stat.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stat.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { s -> draft = s.filter { it.isDigit() }.take(7) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.8f),
            )
            FilledTonalButton(
                enabled = draft.toIntOrNull() != null && draft.toIntOrNull() != stat.value,
                onClick = { draft.toIntOrNull()?.let { onSetAsuraStat(stat.statId, it) } },
            ) {
                Text(tr("Đặt", "Set"))
            }
        }
    }
}
