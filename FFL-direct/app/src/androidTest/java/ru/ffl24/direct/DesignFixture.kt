package ru.ffl24.direct

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import ru.ffl24.direct.domain.*
import java.util.concurrent.atomic.AtomicInteger

/** UI-only examples. None of the identities, dates or contacts come from the design or DB. */
internal class DesignFixture(var mode:String="normal",var session:DriverSession?=null):DirectRuntime {
    val reads=AtomicInteger();val deliveries=AtomicInteger()
    var gate:CompletableDeferred<Unit>?=null
    var failure:DetailsResult<Nothing>?=null
    var revision=0L
    var revoked=false
    private val delivered=mutableSetOf<OrderId>()
    val contacts get()=if(mode=="ambiguous")OrderContacts(addressSource=AddressSource.AMBIGUOUS)else OrderContacts(
        address="Тестовый город, Примерная, 10",routeText="Проверочный адрес",routeUrl="https://yandex.ru/maps/",
        addressSource=AddressSource.MENEGER_CONTR_FFL,confidence=MatchConfidence.HIGH,clientPhone="+70000000001",loaderPhone="+70000000002",managerName="Тестовый менеджер",managerPhone="+70000000003")
    fun rows(date:String)=if(mode=="empty")emptyList()else listOf(
        row(OrderSource.REYS,1,"FF-101",2,2,date),row(OrderSource.REYS,2,"FF-102",2,1,date),row(OrderSource.REYS,3,"Без номера",1,0,date),
        row(OrderSource.REYS_DEN,1,"FF-201",2,2,date),row(OrderSource.REYS,4,"FF-099",2,2,date,true),row(OrderSource.REYS_DEN,2,"FF-098",2,2,date,true))
    private fun row(source:OrderSource,id:Long,number:String,status:Long,quantity:Long,date:String,wasDelivered:Boolean=false):DriverOrder {
        val key=OrderId(source,id);val done=wasDelivered||key in delivered
        return DriverOrder(key,session?.driverId?:97,session?.displayName,date,number,"Демонстрационный получатель с длинным названием",null,id,3+revision,status,quantity,done,if(done)"2099-12-31 10:15:00"else null,contacts)
    }
    override val startupRepository=object:StartupRepository{override fun readiness()=if(mode=="unconfigured")RuntimeReadiness.ConfigurationMissing else RuntimeReadiness.ConnectionNotChecked}
    override val authRepository=object:AuthRepository {
        override suspend fun getDrivers()=AuthResult.Success(listOf(Driver(97,"Тестовый водитель"),Driver(98,"Второй тестовый водитель"),Driver(99,"Демонстрационный водитель"),Driver(100,"Проверочный водитель А"),Driver(101,"Проверочный водитель Б"),Driver(102,"Проверочный водитель В")))
        override suspend fun restore():AuthResult<DriverSession?> = when(mode){"loading"->awaitCancellation();"error"->AuthResult.Failed(AuthError.CONNECTION_UNAVAILABLE);"disabled"->AuthResult.DriverDisabled;else->if(revoked)AuthResult.DriverDisabled else AuthResult.Success(session)}
        override suspend fun checkAccess()=if(revoked)AuthResult.DriverDisabled else AuthResult.Success(session)
        override suspend fun logout():AuthResult<Unit>{session=null;return AuthResult.Success(Unit)}
        override suspend fun login(driverId:Long,password:CharArray):AuthResult<DriverSession> = if(password.concatToString()=="demo-only")AuthResult.Success(DriverSession(driverId,if(driverId==97L)"Тестовый водитель"else"Второй тестовый водитель").also{session=it})else AuthResult.InvalidCredentials
    }
    override val workspaceRepository=object:DriverWorkspaceRepository {
        override suspend fun orders(date:String):DetailsResult<List<DriverOrder>>{reads.incrementAndGet();gate?.await();return failure?:DetailsResult.Success(rows(date))}
        override suspend fun directory()=DetailsResult.Success(PhoneDirectory("0000 0000 0000 0000","Тестовый получатель",listOf(DirectoryPhone("Диспетчер (тест)","+70000000001"),DirectoryPhone("Склад (тест)","+70000000002")),listOf(DriverPhone(97,"Тестовый водитель","+70000000003"),DriverPhone(98,"Второй тестовый водитель","+70000000004"))))
        override suspend fun summary(date:String)=DetailsResult.Success(NotificationSummary(3,1,2,listOf(OrderId(OrderSource.REYS_DEN,1))))
        override suspend fun deliver(id:OrderId):DetailsResult<DeliveryReceipt>{val fresh=delivered.add(id);if(fresh)deliveries.incrementAndGet();return DetailsResult.Success(DeliveryReceipt(if(fresh)DeliveryStatus.DELIVERED else DeliveryStatus.ALREADY_DELIVERED,"2099-12-31 12:00:00"))}
    }
    override val orderDetailsRepository=object:OrderDetailsRepository {
        override suspend fun validateRouteOpened(id:OrderId)=DetailsResult.Success(Unit)
        override suspend fun getDetails(id:OrderId):DetailsResult<OrderDetails> {
            val row=rows("2099-12-31").firstOrNull{it.id==id}?:return DetailsResult.NotFound
            return DetailsResult.Success(OrderDetails(id,0,row.orderNumber,"Демонстрационный получатель","Демонстрационный получатель","Пример исходного адреса","С 11:30. Позвонить за 15 минут: +70000000004","С 11:30","11:30","Позвонить за 15 минут. Въезд со стороны двора.",row.invoiceCount,row.status,row.quantityReadiness,row.isDelivered,row.deliveredAt,contacts,
                (1..5).map{PreviousDriver(100L+it,"Тестовый водитель №$it","+7000000000$it","+7000000000${it+1}","2099-12-${30-it}",OrderId(id.source,100L+it),"FF-${100-it}")}))
        }
    }
}
