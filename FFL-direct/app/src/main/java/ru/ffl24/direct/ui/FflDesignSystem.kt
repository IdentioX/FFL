package ru.ffl24.direct.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.navigation.OrderExternalActions
import ru.ffl24.direct.navigation.PhoneActions
import ru.ffl24.direct.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

object FflSpace { val screen=16.dp;val gap=10.dp;val cardRadius=18.dp;val row=60.dp;val control=48.dp }
enum class FflSymbol { Database,Server,Home,Book,Card,Refresh,Sort,Settings,Back,Chevron,User,Search,Phone,Copy,Pin,Clock,Box,Calendar,Bell,Info,Check,Close,Eye,EyeOff,Grip,Route,Lock,Sun,Logout }

/** One consistent 24-unit outline icon set; no remote images or icon fonts. */
@Composable fun FflIcon(symbol:FflSymbol,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary,description:String?=null) {
    Canvas(modifier.size(24.dp).then(if(description==null)Modifier else Modifier.semantics {contentDescription=description})) {
        withTransform({scale(size.width/24,size.height/24,pivot=Offset.Zero)}) {
            val stroke=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x,y),Offset(x2,y2),strokeWidth=1.7f,cap=StrokeCap.Round)
            fun path(vararg points:Float) {val p=Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);drawPath(p,color,style=stroke)}
            fun circle(x:Float,y:Float,r:Float)=drawCircle(color,r,Offset(x,y),style=stroke)
            fun rect(x:Float,y:Float,w:Float,h:Float,r:Float=2f)=drawRoundRect(color,Offset(x,y),Size(w,h),CornerRadius(r),style=stroke)
            when(symbol) {
                FflSymbol.Database->{drawOval(color,Offset(5f,2f),Size(14f,6f),style=stroke);line(5f,5f,5f,19f);line(19f,5f,19f,19f);drawArc(color,0f,180f,false,Offset(5f,9f),Size(14f,6f),style=stroke);drawArc(color,0f,180f,false,Offset(5f,16f),Size(14f,6f),style=stroke)}
                FflSymbol.Server->{rect(3f,3f,18f,7f);rect(3f,14f,18f,7f);line(6f,6.5f,9f,6.5f);line(6f,17.5f,9f,17.5f)}
                FflSymbol.Home->{path(3f,10f,12f,3f,21f,10f);path(5f,9f,5f,21f,10f,21f,10f,14f,14f,14f,14f,21f,19f,21f,19f,9f)}
                FflSymbol.Book->{rect(5f,3f,15f,18f);line(9f,3f,9f,21f);circle(14.5f,9f,2f);drawArc(color,180f,180f,false,Offset(11.5f,13f),Size(6f,5f),style=stroke);line(3f,7f,6f,7f);line(3f,16f,6f,16f)}
                FflSymbol.Card->{rect(2f,5f,20f,14f);line(2f,10f,22f,10f);line(6f,15f,10f,15f)}
                FflSymbol.Refresh->{drawArc(color,-145f,130f,false,Offset(4f,4f),Size(16f,16f),style=stroke);drawArc(color,35f,130f,false,Offset(4f,4f),Size(16f,16f),style=stroke);path(17f,4f,20f,8f,15f,8f);path(7f,20f,4f,16f,9f,16f)}
                FflSymbol.Sort->{line(7f,4f,7f,20f);path(3f,8f,7f,4f,11f,8f);line(17f,4f,17f,20f);path(13f,16f,17f,20f,21f,16f)}
                FflSymbol.Settings->{val p=Path();for(i in 0..47) {val a=i*PI/24;val r=if(i%6 in 1..4)9.0 else 7.0;val x=12+(cos(a)*r).toFloat();val y=12+(sin(a)*r).toFloat();if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};p.close();drawPath(p,color,style=stroke);circle(12f,12f,3f)}
                FflSymbol.Back->{line(4f,12f,21f,12f);path(10f,5f,3f,12f,10f,19f)}
                FflSymbol.Chevron->path(9f,5f,16f,12f,9f,19f)
                FflSymbol.User->{circle(12f,7f,4f);drawArc(color,180f,180f,false,Offset(4f,13f),Size(16f,14f),style=stroke);line(4f,20f,20f,20f)}
                FflSymbol.Search->{circle(10f,10f,6.5f);line(15f,15f,21f,21f)}
                FflSymbol.Phone->{val p=Path();p.moveTo(5f,3f);p.cubicTo(1f,5f,5f,15f,10f,19f);p.cubicTo(15f,23f,20f,21f,21f,17f);p.lineTo(16f,14f);p.lineTo(13f,17f);p.cubicTo(10f,15f,8f,12f,7f,9f);p.lineTo(10f,7f);p.lineTo(7f,3f);p.close();drawPath(p,color)}
                FflSymbol.Copy->{rect(8f,6f,12f,15f);path(15f,6f,15f,3f,4f,3f,4f,17f,8f,17f)}
                FflSymbol.Pin->{val p=Path();p.moveTo(12f,22f);p.cubicTo(9f,18f,4f,12f,4f,8f);p.cubicTo(4f,-1f,20f,-1f,20f,8f);p.cubicTo(20f,12f,15f,18f,12f,22f);p.close();drawPath(p,color,style=stroke);circle(12f,8f,2.5f)}
                FflSymbol.Clock->{circle(12f,12f,9f);path(12f,6f,12f,12f,16f,14f)}
                FflSymbol.Box->{path(12f,2f,22f,7f,22f,18f,12f,23f,2f,18f,2f,7f,12f,2f);path(2f,7f,12f,12f,22f,7f);line(12f,12f,12f,23f);line(7f,4.5f,17f,9.5f)}
                FflSymbol.Calendar->{rect(3f,5f,18f,17f);line(3f,10f,21f,10f);line(7f,2f,7f,7f);line(17f,2f,17f,7f);rect(7f,13f,3f,3f,.5f)}
                FflSymbol.Bell->{path(4f,17f,7f,14f,7f,8f,8f,5f,12f,3f,16f,5f,17f,8f,17f,14f,20f,17f,4f,17f);drawArc(color,0f,180f,false,Offset(9f,18f),Size(6f,4f),style=stroke)}
                FflSymbol.Info->{circle(12f,12f,9f);line(12f,11f,12f,17f);drawCircle(color,1f,Offset(12f,7f))}
                FflSymbol.Check->path(5f,12f,10f,17f,20f,6f)
                FflSymbol.Close->{line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
                FflSymbol.Eye,FflSymbol.EyeOff->{val p=Path();p.moveTo(2f,12f);p.cubicTo(8f,2f,16f,2f,22f,12f);p.cubicTo(16f,22f,8f,22f,2f,12f);drawPath(p,color,style=stroke);circle(12f,12f,3f);if(symbol==FflSymbol.EyeOff)line(3f,3f,21f,21f)}
                FflSymbol.Grip->{for(x in listOf(9f,15f))for(y in listOf(6f,12f,18f))drawCircle(color,1.3f,Offset(x,y))}
                FflSymbol.Route->path(2f,10f,22f,2f,15f,22f,11f,13f,2f,10f,22f,2f,11f,13f)
                FflSymbol.Lock->{rect(5f,10f,14f,12f);drawArc(color,180f,180f,false,Offset(8f,1f),Size(8f,16f),style=stroke);line(12f,15f,12f,18f)}
                FflSymbol.Sun->{circle(12f,12f,4f);for(i in 0..7){val a=i*PI/4;line(12+(cos(a)*7).toFloat(),12+(sin(a)*7).toFloat(),12+(cos(a)*10).toFloat(),12+(sin(a)*10).toFloat())}}
                FflSymbol.Logout->{path(10f,3f,3f,3f,3f,21f,10f,21f);line(9f,12f,21f,12f);path(16f,7f,21f,12f,16f,17f)}
            }
        }
    }
}

@Composable fun FflCard(modifier:Modifier=Modifier,padding:Dp=14.dp,gap:Dp=10.dp,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(FflSpace.cardRadius),color=MaterialTheme.colorScheme.surface,shadowElevation=1.dp) {
        Column(Modifier.padding(padding),verticalArrangement=Arrangement.spacedBy(gap),content=content)
    }
}
@Composable fun FflPrimaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,busy:Boolean=false,icon:FflSymbol?=null) {
    val shape=RoundedCornerShape(14.dp)
    Button(onClick,modifier.heightIn(min=50.dp).then(if(enabled)Modifier.background(Brush.horizontalGradient(listOf(AppPrimary,Color(0xFF6944F2))),shape)else Modifier),enabled=enabled&&!busy,shape=shape,
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=Color.White,disabledContainerColor=MaterialTheme.colorScheme.primaryContainer,disabledContentColor=MaterialTheme.colorScheme.onPrimaryContainer),contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp)) {
        if(busy) {CircularProgressIndicator(Modifier.size(20.dp),color=if(enabled)Color.White else MaterialTheme.colorScheme.primary,strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
        if(icon!=null&&!busy) {FflIcon(icon,Modifier.size(19.dp),if(enabled)Color.White else MaterialTheme.colorScheme.onPrimaryContainer);Spacer(Modifier.width(8.dp))}
        Text(text,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable fun FflSecondaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,icon:FflSymbol?=null) {
    FilledTonalButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary)) {
        if(icon!=null){FflIcon(icon,Modifier.size(19.dp));Spacer(Modifier.width(8.dp))};Text(text)
    }
}
@Composable fun FflRoundAction(symbol:FflSymbol,description:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    IconButton(onClick,modifier.sizeIn(minWidth=40.dp,minHeight=40.dp),enabled=enabled) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if(enabled)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)),contentAlignment=Alignment.Center) {
            FflIcon(symbol,Modifier.size(20.dp),if(enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f),description)
        }
    }
}
@Composable fun FflAvatar(name:String,modifier:Modifier=Modifier,large:Boolean=false) {
    Box(modifier.size(if(large)76.dp else 40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.11f)),contentAlignment=Alignment.Center) {
        FflIcon(FflSymbol.User,Modifier.size(if(large)44.dp else 25.dp),description=name)
    }
}
@Composable fun FflStatus(text:String,color:Color=AppReady) {
    Surface(color=color.copy(alpha=.12f),shape=RoundedCornerShape(8.dp)) {Text(text,Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,color=if(color==AppReady) {if(MaterialTheme.colorScheme.surface.luminance()<.2f)Color(0xFF8DE2B9)else Color(0xFF17734D)}else MaterialTheme.colorScheme.onSurface)}
}
@Composable fun FflSearch(value:String,changed:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value,changed,modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(13.dp),textStyle=MaterialTheme.typography.bodyMedium,
        placeholder={Text(placeholder,style=MaterialTheme.typography.bodyMedium)},leadingIcon={FflIcon(FflSymbol.Search,Modifier.size(19.dp),MaterialTheme.colorScheme.onSurfaceVariant)},
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=MaterialTheme.colorScheme.outline,focusedBorderColor=MaterialTheme.colorScheme.primary))
}
@Composable fun FflStatePanel(title:String,body:String,icon:FflSymbol=FflSymbol.Info,loading:Boolean=false,error:Boolean=false,action:String?=null,onAction:()->Unit={},modifier:Modifier=Modifier) {
    Box(modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center) {
        FflCard(Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(loading) CircularProgressIndicator(Modifier.size(38.dp),strokeWidth=3.dp) else Box(Modifier.size(52.dp).clip(CircleShape).background(if(error)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {FflIcon(icon,Modifier.size(28.dp),if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}
            Text(title,style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(action!=null) FflPrimaryButton(action,onAction,Modifier.padding(top=4.dp))
        }}
    }
}
fun displayDate(date:String):String=runCatching {LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag("ru")))}.getOrDefault(date)
fun orderCount(n:Int):String="$n "+when { n%100 in 11..14->"заказов";n%10==1->"заказ";n%10 in 2..4->"заказа";else->"заказов" }
fun invoiceCount(n:Long):String="$n "+when {n%100 in 11..14->"накладных";n%10==1L->"накладная";n%10 in 2L..4L->"накладные";else->"накладных"}
fun compactStatus(delivered:Boolean,status:Long,quantity:Long):String=when {delivered->"Сдано";status==1L->"Не готово";status!=2L->"Неизвестно";quantity==1L->"Частично";quantity==0L->"Накладные не готовы";else->"Готово"}
fun readinessColor(delivered:Boolean,status:Long,quantity:Long):Color=when {delivered->AppSubmitted;status==2L&&quantity==2L->AppReady;status==2L&&quantity==1L->AppWarning;else->AppSubmitted}
fun copyFfl(context:Context,value:String) {(context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("FFL",value))}
@Composable fun FflCall(phone:String?,label:String="Позвонить",modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val enabled=phone!=null&&PhoneActions.dialNumber(phone)!=null
    FflRoundAction(FflSymbol.Phone,if(enabled)label else "Телефон не указан",{
        if(phone!=null&&!OrderExternalActions.dial(context,phone)) android.widget.Toast.makeText(context,"Нет приложения для звонков",android.widget.Toast.LENGTH_SHORT).show()
    },modifier,enabled)
}

fun displayOrderNumber(value:String)=if(value=="Без номера"||value.startsWith("№")||value.startsWith("#"))value else "№$value"
