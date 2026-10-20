package ru.ffl24.direct.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.ffl24.direct.domain.*

@Composable fun DirectoryContent(result:DetailsResult<PhoneDirectory>?,refresh:()->Unit) {
    if(result !is DetailsResult.Success){ResultMessage(result,refresh);return}
    val directory=result.value;val context=LocalContext.current
    var query by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableIntStateOf(0)}
    fun matches(name:String?,phone:String):Boolean {
        val q=query.trim();if(q.isEmpty())return true
        val digits=q.filter(Char::isDigit)
        return name.orEmpty().contains(q,true)||phone.contains(q,true)||(digits.isNotEmpty()&&phone.filter(Char::isDigit).contains(digits))
    }
    val phones=directory.phones.filter{matches(it.name,it.phone)};val drivers=directory.drivers.filter{matches(it.name,it.phone)}
    LazyColumn(Modifier.fillMaxSize().testTag("phone_directory"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
        item {FflSearch(query,{query=it},"Поиск по имени или телефону",Modifier.padding(bottom=12.dp).testTag("directory_search"))}
        item {Row(Modifier.padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Важные","Водители","Все").forEachIndexed {i,title->FilterChip(category==i,{category=i},label={Text(title,style=MaterialTheme.typography.labelMedium)},shape=RoundedCornerShape(12.dp),border=null,
                colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary,containerColor=MaterialTheme.colorScheme.primaryContainer,labelColor=MaterialTheme.colorScheme.onSurface),modifier=Modifier.testTag("directory_category_$i"))}
        }}
        if(category!=1&&query.isBlank())item {
            FflCard(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
                Text("Платёжная карта",style=MaterialTheme.typography.titleSmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){FflIcon(FflSymbol.Card,Modifier.size(21.dp))}
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                        Text("Номер карты",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(directory.cardNumber?:"Номер карты не указан",style=MaterialTheme.typography.bodyLarge)
                        Text(directory.cardHolder?:"Владелец карты не указан",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    directory.cardNumber?.let{number->IconButton({copyFfl(context,number)},Modifier.testTag("copy_card")){FflIcon(FflSymbol.Copy,Modifier.size(20.dp),description="Копировать карту")}}
                }
            }
        }
        if(category!=1) {
            if(category==2)item{Text("Важные телефоны",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.titleSmall)}
            itemsIndexed(phones){i,phone->DirectoryRow(phone.name?:"Телефон",phone.phone,i==0,i==phones.lastIndex)}
            if(phones.isEmpty())item{DirectoryEmpty(if(query.isBlank())"Важные телефоны не указаны"else "Ничего не найдено")}
        }
        if(category!=0) {
            if(category==2)item{Text("Активные водители",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.titleSmall)}
            itemsIndexed(drivers,key={_,driver->driver.id}){i,driver->DirectoryRow(driver.name,driver.phone,i==0,i==drivers.lastIndex)}
            if(drivers.isEmpty())item{DirectoryEmpty(if(query.isBlank())"Водителей с телефоном нет"else "Ничего не найдено")}
        }
    }
}
@Composable private fun DirectoryEmpty(text:String){FflCard(Modifier.fillMaxWidth()){Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun DirectoryRow(name:String,number:String,first:Boolean,last:Boolean) {
    val context=LocalContext.current
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=if(first)18.dp else 0.dp,topEnd=if(first)18.dp else 0.dp,bottomStart=if(last)18.dp else 0.dp,bottomEnd=if(last)18.dp else 0.dp)) {
        Column {
            Row(Modifier.heightIn(min=74.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(number,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=3.dp))}
                IconButton({copyFfl(context,number)},Modifier.size(36.dp)){FflIcon(FflSymbol.Copy,Modifier.size(18.dp),description="Копировать номер $name")}
                FflCall(number,"Позвонить: $name")
            }
            if(!last)HorizontalDivider(Modifier.padding(horizontal=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
        }
    }
}
