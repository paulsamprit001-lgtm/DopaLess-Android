package com.dopaless.app

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.min

@Composable
fun GrowthScreen(data: SharedPreferences, hourly: List<Long>, dailyUsage: Long) {
    var challenge by remember { mutableIntStateOf(data.getInt("journey_days", 3)) }
    var gardenOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_000L); refresh++ } }
    val refreshTick = refresh
    val focus = remember(refreshTick) { data.getInt("focus_done", 0) }
    val done = remember(refreshTick) { data.getInt("challenge_done", 0) }
    val reclaimed = remember(refreshTick) { data.getLong("garden_reclaimed_minutes", 0L) }
    val weekMinutes = dailyUsage / 60000L * 7L
    val yearHours = dailyUsage / 3600000L * 365L
    if (gardenOpen) {
        GardenWorldScreen(focus, done, reclaimed) { gardenOpen = false }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Growth", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
            item {
                Card(Modifier.fillMaxWidth().clickable { gardenOpen = true }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR DIGITAL WORLD", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text("Explore  ›", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        Text("A world that grows with you", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        GardenPreview(focus, done, reclaimed)
                        Text(focus.toString() + " focus sessions · " + done + " challenges completed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Your progress stays. Missing a day never takes anything away.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LIFE PROGRESS CALCULATOR", color = MaterialTheme.colorScheme.primary)
                    Text("If today's screen time stayed similar:")
                    Text((weekMinutes / 60).toString() + " hours / week", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(yearHours.toString() + " hours / year", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } }
            }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DISTRACTION HEATMAP", color = MaterialTheme.colorScheme.primary)
                    Text("Hourly phone activity today")
                    val peak = (hourly.maxOrNull() ?: 1L).coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        hourly.forEach { value ->
                            Box(Modifier.weight(1f).height((8f + 48f * value / peak).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0) .9f else .15f), RoundedCornerShape(3.dp)))
                        }
                    }
                    Text("12 AM                       6 AM                       12 PM                       6 PM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PERSONAL 3 / 7 / 30-DAY CHALLENGE", color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 30).forEach { days ->
                            FilterChip(challenge == days, { challenge = days; data.edit().putInt("journey_days", days).apply() }, label = { Text(days.toString() + " days") })
                        }
                    }
                    Text("Selected challenge: " + challenge + " days")
                    Text("Focus sessions: " + focus + " · Challenges completed: " + done, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        }
    }
}

@Composable
private fun GardenPreview(focus: Int, challenges: Int, reclaimed: Long) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val night = hour < 6 || hour >= 18
    Canvas(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(if (night) listOf(Color(0xFF17283C), Color(0xFF40566A)) else listOf(Color(0xFF9FD2D0), Color(0xFFE8D5A4))))
        drawCircle(if (night) Color(0xFFE8E3C2) else Color(0xFFFFE6A0), 18.dp.toPx(), Offset(w * .82f, h * .22f))
        val hill = Path().apply { moveTo(0f,h*.68f); quadraticTo(w*.24f,h*.38f,w*.48f,h*.68f); quadraticTo(w*.76f,h*.38f,w,h*.62f); lineTo(w,h); lineTo(0f,h); close() }
        drawPath(hill, Color(0xFF527E61))
        drawOval(Color(0xFF315F4B), Offset(w*.12f,h*.69f), Size(w*.78f,h*.42f))
        repeat(min(focus, 10)) { i ->
            val x = w * (.12f + i * .072f); val y = h * (.78f + (i % 2) * .025f)
            drawLine(Color(0xFF315C3D), Offset(x,y), Offset(x,y-12.dp.toPx()), 2.dp.toPx())
            drawCircle(if (i%2==0) Color(0xFFF4C86A) else Color(0xFFE8A7B5), 4.dp.toPx(), Offset(x,y-14.dp.toPx()))
        }
        if (reclaimed >= 60L) {
            drawRect(Color(0xFF76523A), Offset(w*.61f,h*.48f), Size(w*.19f,h*.25f))
            val roof = Path().apply { moveTo(w*.57f,h*.5f); lineTo(w*.705f,h*.34f); lineTo(w*.84f,h*.5f); close() }
            drawPath(roof, Color(0xFF47382F))
            drawRect(Color(0xFFFFD27A), Offset(w*.68f,h*.58f), Size(w*.045f,h*.07f))
        }
        if (challenges >= 1) drawCircle(Color(0xFFE8E4D7), 5.dp.toPx(), Offset(w*.43f,h*.72f))
        if (challenges >= 3) drawCircle(Color(0xFFB7C7D5), 4.dp.toPx(), Offset(w*.48f,h*.69f))
        if (focus + challenges >= 10) repeat(5) { i -> drawCircle(Color(0xFFFFE7A1), 2.dp.toPx(), Offset(w*(.18f+i*.14f),h*(.15f+(i%2)*.1f))) }
    }
}

@Composable
private fun GardenWorldScreen(focus: Int, challenges: Int, reclaimed: Long, onBack: () -> Unit) {
    var clock by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var region by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); clock = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) } }
    val night = clock < 6 || clock >= 18
    val grove = challenges >= 1; val coast = challenges >= 3
    val cabin = reclaimed >= 60L; val lookout = reclaimed >= 180L
    val rabbit = focus >= 3; val fox = focus >= 8; val rare = focus + challenges >= 10
    val title = when (region) { 1 -> "Whispering Grove"; 2 -> "Moonlit Coast"; else -> "Meadow Haven" }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("‹  Growth") }
            Spacer(Modifier.weight(1f))
            Text(if (night) "Night world" else "Day world", color=MaterialTheme.colorScheme.primary, fontSize=12.sp, fontWeight=FontWeight.SemiBold)
        }
        Text(title, Modifier.padding(horizontal=20.dp), fontSize=25.sp, fontWeight=FontWeight.SemiBold)
        Text("A little world, shaped by the time you reclaim.", Modifier.padding(horizontal=20.dp, vertical=4.dp), color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=13.sp)
        GardenScene(night, region, focus, challenges, cabin, lookout, rabbit, fox, rare)
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(region==0, { region=0 }, label={ Text("Meadow") })
            FilterChip(region==1, { region=1 }, enabled=grove, label={ Text(if(grove) "Grove" else "Grove · 1 challenge") })
            FilterChip(region==2, { region=2 }, enabled=coast, label={ Text(if(coast) "Coast" else "Coast · 3 challenges") })
        }
        LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(horizontal=18.dp, vertical=8.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item { Text("YOUR WORLD'S STORY", color=MaterialTheme.colorScheme.primary, fontSize=11.sp, fontWeight=FontWeight.Bold) }
            item { GardenMilestone("🌱", "Growing plants", focus.toString()+" focus sessions · each session adds new life", true) }
            item { GardenMilestone("🦋", "Wildlife", if(fox) "Rabbits and foxes have made this place home" else if(rabbit) "Rabbits have started visiting" else "Complete 3 focus sessions to welcome wildlife", rabbit) }
            item { GardenMilestone("🏡", "New buildings", if(lookout) "Cabin and hilltop lookout unlocked" else if(cabin) "A cosy cabin has appeared · "+(180L-reclaimed.coerceAtMost(180L))+" more minutes for the lookout" else "Reclaim 60 minutes through completed timers to build your cabin", cabin) }
            item { GardenMilestone("✨", "Rare environment", if(rare) "A rare meteor shower and glowing flowers appear" else "Reach 10 completed sessions and challenges for a rare surprise", rare) }
            item { Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("No streaks. No decay.", fontWeight=FontWeight.SemiBold)
                Text("Your garden never withers when you take a break. Every unlock stays yours, and the world keeps your progress safe.", color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=12.sp)
            } } }
        }
    }
}

@Composable
private fun GardenScene(night:Boolean, region:Int, focus:Int, challenges:Int, cabin:Boolean, lookout:Boolean, rabbit:Boolean, fox:Boolean, rare:Boolean) {
    Canvas(Modifier.fillMaxWidth().height(258.dp).padding(horizontal=12.dp).clip(RoundedCornerShape(22.dp))) {
        val w=size.width; val h=size.height
        drawRect(Brush.verticalGradient(if(night) listOf(Color(0xFF17263C),Color(0xFF4B526D)) else listOf(Color(0xFF7FC3D0),Color(0xFFF4D3A0))))
        drawCircle(if(night) Color(0xFFE7E4D1) else Color(0xFFFFE4A0),23.dp.toPx(),Offset(w*.82f,h*.18f))
        if(night) repeat(18) { i -> drawCircle(Color(0xFFFFF1C5).copy(alpha=.75f),1.5.dp.toPx(),Offset(w*((i*37%97)/100f),h*((i*19%42)/100f))) }
        val far=Path().apply { moveTo(0f,h*.57f); quadraticTo(w*.22f,h*.28f,w*.47f,h*.58f); quadraticTo(w*.73f,h*.31f,w,h*.52f); lineTo(w,h); lineTo(0f,h); close() }
        drawPath(far,if(night) Color(0xFF314A4D) else Color(0xFF7D9B70))
        val near=Path().apply { moveTo(0f,h*.72f); quadraticTo(w*.3f,h*.47f,w*.56f,h*.72f); quadraticTo(w*.8f,h*.52f,w,h*.68f); lineTo(w,h); lineTo(0f,h); close() }
        drawPath(near,if(night) Color(0xFF1E453D) else Color(0xFF477A51))
        if(region==2) {
            drawRect(Color(0xFF477F9B),Offset(0f,h*.72f),Size(w,h*.28f))
            repeat(7) { i -> drawLine(Color(0xFFB7D7D5).copy(alpha=.65f),Offset(w*(i/7f),h*(.78f+(i%2)*.04f)),Offset(w*(i/7f+.08f),h*(.78f+(i%2)*.04f)),2.dp.toPx()) }
        } else drawOval(if(night) Color(0xFF244F52) else Color(0xFF75A86E),Offset(w*.08f,h*.65f),Size(w*.84f,h*.43f))
        if(region==1) repeat(7) { i ->
            val x=w*(.12f+i*.12f)
            drawRect(Color(0xFF654B39),Offset(x,h*.36f),Size(7.dp.toPx(),h*.48f))
            drawCircle(if(night) Color(0xFF244B43) else Color(0xFF326D4B),25.dp.toPx(),Offset(x+3.dp.toPx(),h*.34f))
        } else repeat(6) { i ->
            val x=w*(.08f+i*.16f); val y=h*(.67f+(i%2)*.035f)
            drawLine(Color(0xFF604B38),Offset(x,y),Offset(x,y-24.dp.toPx()),4.dp.toPx())
            drawCircle(if(night) Color(0xFF24533F) else Color(0xFF2F7047),17.dp.toPx(),Offset(x,y-30.dp.toPx()))
            drawCircle(if(night) Color(0xFF35684B) else Color(0xFF4D8B53),12.dp.toPx(),Offset(x-8.dp.toPx(),y-25.dp.toPx()))
        }
        repeat(min(focus,24)) { i ->
            val x=w*(.12f+(i%12)*.067f); val y=h*(.84f+(i/12)*.045f)
            drawLine(Color(0xFF285B3B),Offset(x,y),Offset(x,y-11.dp.toPx()),2.dp.toPx())
            drawCircle(listOf(Color(0xFFFFD17D),Color(0xFFF1A8B8),Color(0xFFECE6A4))[i%3],4.dp.toPx(),Offset(x,y-13.dp.toPx()))
        }
        if(cabin) {
            val x=w*.60f; val y=h*.48f
            drawRect(Color(0xFF79543A),Offset(x,y),Size(w*.19f,h*.25f))
            val roof=Path().apply { moveTo(x-8.dp.toPx(),y+2.dp.toPx()); lineTo(x+w*.095f,y-h*.14f); lineTo(x+w*.20f,y+2.dp.toPx()); close() }
            drawPath(roof,Color(0xFF47372E))
            drawRect(if(night) Color(0xFFFFD27B) else Color(0xFFB5D9D7),Offset(x+w*.07f,y+h*.08f),Size(w*.045f,h*.07f))
        }
        if(lookout) {
            drawRect(Color(0xFF8A6C4C),Offset(w*.25f,h*.46f),Size(w*.035f,h*.20f))
            drawRoundRect(Color(0xFF8A6C4C),Offset(w*.20f,h*.43f),Size(w*.14f,h*.045f),androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
        }
        if(rabbit) {
            drawCircle(Color(0xFFE7DED0),7.dp.toPx(),Offset(w*.47f,h*.79f))
            drawOval(Color(0xFFE7DED0),Offset(w*.45f,h*.73f),Size(4.dp.toPx(),12.dp.toPx()))
            drawOval(Color(0xFFE7DED0),Offset(w*.48f,h*.72f),Size(4.dp.toPx(),12.dp.toPx()))
        }
        if(fox) { drawCircle(Color(0xFFC97D43),7.dp.toPx(),Offset(w*.55f,h*.82f)); drawCircle(Color(0xFFF1D7B7),3.dp.toPx(),Offset(w*.56f,h*.82f)) }
        if(challenges>=1) { drawCircle(Color(0xFFFFE6A0),4.dp.toPx(),Offset(w*.37f,h*.71f)); drawCircle(Color(0xFFFFE6A0),4.dp.toPx(),Offset(w*.39f,h*.69f)) }
        if(rare) repeat(9) { i -> drawCircle(Color(0xFFFFE9A6).copy(alpha=.85f),2.dp.toPx(),Offset(w*(.08f+(i*13%84)/100f),h*(.12f+(i*17%28)/100f))) }
    }
}

@Composable
private fun GardenMilestone(icon:String,title:String,detail:String,unlocked:Boolean) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
            Text(icon,fontSize=23.sp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,fontWeight=FontWeight.SemiBold)
                Text(detail,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if(unlocked) "Unlocked" else "Locked",color=if(unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)
        }
    }
}
