package com.dashlane.item.subview.provider.id

import android.content.Context
import com.dashlane.R
import com.dashlane.inapplogin.UsageLogCode35Action
import com.dashlane.item.ItemEditViewContract
import com.dashlane.item.ScreenConfiguration
import com.dashlane.item.header.ItemHeader
import com.dashlane.item.subview.ItemScreenConfigurationProvider
import com.dashlane.item.subview.ItemSubView
import com.dashlane.item.subview.ItemSubViewWithActionWrapper
import com.dashlane.item.subview.action.CopyAction
import com.dashlane.item.subview.provider.DateTimeFieldFactory
import com.dashlane.item.subview.provider.SubViewFactory
import com.dashlane.item.subview.provider.createCountryField
import com.dashlane.session.BySessionRepository
import com.dashlane.session.SessionManager
import com.dashlane.storage.userdata.accessor.MainDataAccessor
import com.dashlane.teamspaces.manager.TeamspaceAccessor
import com.dashlane.teamspaces.model.Teamspace
import com.dashlane.useractivity.log.usage.UsageLogCode11
import com.dashlane.useractivity.log.usage.UsageLogCode35
import com.dashlane.useractivity.log.usage.UsageLogRepository
import com.dashlane.util.clipboard.vault.CopyField
import com.dashlane.util.isNotSemanticallyNull
import com.dashlane.vault.model.VaultItem
import com.dashlane.vault.model.copySyncObject
import com.dashlane.vault.summary.toSummary
import com.dashlane.xml.domain.SyncObject
import java.time.LocalDate

class ItemScreenConfigurationPassportProvider(
    private val teamspaceAccessor: TeamspaceAccessor,
    private val mainDataAccessor: MainDataAccessor,
    sessionManager: SessionManager,
    bySessionUsageLogRepository: BySessionRepository<UsageLogRepository>,
    private val dateTimeFieldFactory: DateTimeFieldFactory
) : ItemScreenConfigurationProvider(
    teamspaceAccessor, mainDataAccessor.getDataCounter(),
    sessionManager, bySessionUsageLogRepository
) {

    @Suppress("UNCHECKED_CAST")
    override fun createScreenConfiguration(
        context: Context,
        item: VaultItem<*>,
        subViewFactory: SubViewFactory,
        editMode: Boolean,
        canDelete: Boolean,
        listener: ItemEditViewContract.View.UiUpdateListener
    ): ScreenConfiguration {
        item as VaultItem<SyncObject.Passport>
        return ScreenConfiguration(
            createSubViews(context, item, subViewFactory, editMode, canDelete, listener),
            createHeader(context, item)
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun hasEnoughDataToSave(itemToSave: VaultItem<*>): Boolean {
        itemToSave as VaultItem<SyncObject.Passport>
        return itemToSave.syncObject.number?.trim().isNotSemanticallyNull()
    }

    private fun createHeader(
        context: Context,
        item: VaultItem<*>
    ): ItemHeader {
        val iconDrawable = createDefaultHeaderIcon(context, item.syncObject)
        val title = context.getString(R.string.passport)
        return ItemHeader(createMenus(), title, iconDrawable)
    }

    private fun createSubViews(
        context: Context,
        item: VaultItem<SyncObject.Passport>,
        subViewFactory: SubViewFactory,
        editMode: Boolean,
        canDelete: Boolean,
        listener: ItemEditViewContract.View.UiUpdateListener
    ): List<ItemSubView<*>> {
        
        val identitySubviews = createIdentitySubviews(
            context,
            subViewFactory,
            mainDataAccessor,
            editMode,
            listener,
            item,
            PassportIdentityAdapter
        )

        val subviews = listOfNotNull(
            
            createCountryField(context, item, editMode),
            
            createNumberField(subViewFactory, context, item, editMode),
            
            createIdDateField(
                item,
                editMode,
                listener,
                item.syncObject.deliveryDate,
                context.getString(R.string.issue_date),
                CopyField.PassportIssueDate,
                VaultItem<*>::copyForUpdatedDeliveryDate
            ),
            
            createPlaceField(subViewFactory, context, item),
            
            createIdDateField(
                item,
                editMode,
                listener,
                item.syncObject.expireDate,
                context.getString(R.string.expiry_date),
                CopyField.PassportExpirationDate,
                VaultItem<*>::copyForUpdatedExpireDate
            ),
            
            createTeamspaceField(subViewFactory, item),
            
            subViewFactory.createSubviewAttachmentDetails(context, item),
            
            dateTimeFieldFactory.createCreationDateField(editMode = editMode, context = context, item = item),
            
            dateTimeFieldFactory.createLatestUpdateDateField(editMode = editMode, context = context, item = item),
            
            subViewFactory.createSubviewDelete(context, listener, canDelete)
        )

        return identitySubviews + subviews
    }

    private fun createTeamspaceField(
        subViewFactory: SubViewFactory,
        item: VaultItem<SyncObject.Passport>
    ): ItemSubView<*>? {
        return if (teamspaceAccessor.canChangeTeamspace()) {
            subViewFactory.createSpaceSelector(
                item.syncObject.spaceId, teamspaceAccessor,
                null, VaultItem<*>::copyForUpdatedTeamspace
            )
        } else {
            null
        }
    }

    private fun createPlaceField(
        subViewFactory: SubViewFactory,
        context: Context,
        item: VaultItem<SyncObject.Passport>
    ): ItemSubView<*>? {
        return subViewFactory.createSubViewString(
            context.getString(R.string.issue_place),
            item.syncObject.deliveryPlace,
            false,
            VaultItem<*>::copyForUpdatedDeliveryPlace
        )
    }

    private fun createNumberField(
        subViewFactory: SubViewFactory,
        context: Context,
        item: VaultItem<SyncObject.Passport>,
        editMode: Boolean
    ): ItemSubView<*>? {
        val number = item.syncObject.number
        val numberView = subViewFactory.createSubViewString(
            context.getString(R.string.passport_hint_number),
            number,
            false,
            VaultItem<*>::copyForUpdatedNumber
        )
        return if (numberView == null || editMode) {
            numberView
        } else {
            ItemSubViewWithActionWrapper(
                numberView,
                CopyAction(item.toSummary(), CopyField.PassportNumber, action = {
                    logger.log(
                        UsageLogCode35(
                            type = UsageLogCode11.Type.PASSPORT.code,
                            action = UsageLogCode35Action.COPY_NUMBER
                        )
                    )
                })
            )
        }
    }
}

private object PassportIdentityAdapter : IdentityAdapter<SyncObject.Passport> {
    override fun fullName(item: VaultItem<SyncObject.Passport>) = item.syncObject.fullname

    override fun withFullName(item: VaultItem<SyncObject.Passport>, fullName: String?):
            VaultItem<SyncObject.Passport> = item.copySyncObject { fullname = fullName }

    override fun gender(item: VaultItem<SyncObject.Passport>): SyncObject.Gender? = item.syncObject.sex

    override fun withGender(
        item: VaultItem<SyncObject.Passport>,
        gender: SyncObject.Gender?
    ): VaultItem<SyncObject.Passport> =
        item.copySyncObject { sex = gender }

    override fun birthDate(item: VaultItem<SyncObject.Passport>) = item.syncObject.dateOfBirth

    override fun withBirthDate(item: VaultItem<SyncObject.Passport>, birthDate: LocalDate?):
            VaultItem<SyncObject.Passport> = item.copySyncObject { dateOfBirth = birthDate }

    override fun linkedIdentity(item: VaultItem<SyncObject.Passport>) = item.syncObject.linkedIdentity

    override fun withLinkedIdentity(item: VaultItem<SyncObject.Passport>, identity: String?):
            VaultItem<SyncObject.Passport> =
        item.copySyncObject { linkedIdentity = identity }
}

@Suppress("UNCHECKED_CAST")
private fun VaultItem<*>.copyForUpdatedTeamspace(value: Teamspace): VaultItem<*> {
    this as VaultItem<SyncObject.Passport>
    val passport = this.syncObject
    return if (value.teamId == passport.spaceId) {
        this
    } else {
        this.copyWithAttrs { teamSpaceId = value.teamId }
    }
}

@Suppress("UNCHECKED_CAST")
private fun VaultItem<*>.copyForUpdatedExpireDate(value: LocalDate?): VaultItem<*> {
    this as VaultItem<SyncObject.Passport>
    val passport = this.syncObject
    return if (value == passport.expireDate) {
        this
    } else {
        this.copySyncObject { expireDate = value }
    }
}

@Suppress("UNCHECKED_CAST")
private fun VaultItem<*>.copyForUpdatedDeliveryPlace(value: String): VaultItem<*> {
    this as VaultItem<SyncObject.Passport>
    val passport = this.syncObject
    return if (value == passport.deliveryPlace.orEmpty()) {
        this
    } else {
        this.copySyncObject { deliveryPlace = value }
    }
}

@Suppress("UNCHECKED_CAST")
private fun VaultItem<*>.copyForUpdatedDeliveryDate(value: LocalDate?): VaultItem<*> {
    this as VaultItem<SyncObject.Passport>
    val passport = this.syncObject
    return if (value == passport.deliveryDate) {
        this
    } else {
        this.copySyncObject { deliveryDate = value }
    }
}

@Suppress("UNCHECKED_CAST")
private fun VaultItem<*>.copyForUpdatedNumber(value: String): VaultItem<*> {
    this as VaultItem<SyncObject.Passport>
    val passport = this.syncObject
    return if (value == passport.number.orEmpty()) {
        this
    } else {
        this.copySyncObject { number = value }
    }
}
