/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */

import spock.lang.*

import org.moqui.context.ExecutionContext
import org.moqui.Moqui

import org.slf4j.LoggerFactory
import org.slf4j.Logger

/* To run these make sure moqui, and mantle are in place and run: "gradle cleanAll load runtime/mantle/mantle-usl:test" */
class WorkProjectBasicFlow extends Specification {
    @Shared
    protected final static Logger logger = LoggerFactory.getLogger(WorkProjectBasicFlow.class)
    @Shared
    ExecutionContext ec
    @Shared
    Map vendorResult, workerResult, clientRateResult, vendorRateResult, clientResult, expInvResult, clientInvResult

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("john.doe", "moqui", null)
        // set an effective date so data check works, etc; Long value (when set from Locale of john.doe, US/Central): 1383411600000
        ec.user.setEffectiveTime(ec.l10n.parseTimestamp("2013-11-02 12:00:00.0", null))
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "create Vendor"() {
        when:
        vendorResult = ec.service.sync().name("mantle.party.PartyServices.create#Organization")
                .parameters([roleTypeId:'VendorBillFrom', organizationName:'Test Vendor']).call()
        Map vendorCiResult = ec.service.sync().name("mantle.party.ContactServices.store#PartyContactInfo")
                .parameters([partyId:vendorResult.partyId, postalContactMechPurposeId:'PostalPayment',
                    telecomContactMechPurposeId:'PhonePayment', emailContactMechPurposeId:'EmailPayment', countryGeoId:'USA',
                    address1:'51 W. Center St.', unitNumber:'1234', city:'Orem', stateProvinceGeoId:'USA_UT',
                    postalCode:'84057', postalCodeExt:'4605', countryCode:'+1', areaCode:'801', contactNumber:'123-4567',
                    emailAddress:'vendor.ar@test.com']).call()
        // internal org and accounting config default settings
        ec.service.sync().name("create#mantle.party.PartyRole").parameters([partyId:vendorResult.partyId, roleTypeId:'OrgInternal']).call()
        ec.service.sync().name("mantle.ledger.LedgerServices.init#PartyAccountingConfiguration")
                .parameters([sourcePartyId:'DefaultSettings', organizationPartyId:vendorResult.partyId]).call()
        // vendor payment/ar rep
        Map vendorRepResult = ec.service.sync().name("mantle.party.PartyServices.create#Account")
                .parameters([firstName:'Vendor', lastName:'TestRep', emailAddress:'vendor.rep@test.com',
                    username:'vendor.rep', newPassword:'moqui1!', newPasswordVerify:'moqui1!', loginAfterCreate:'false']).call()
        Map repRelResult = ec.service.sync().name("create#mantle.party.PartyRelationship")
                .parameters([relationshipTypeEnumId:'PrtRepresentative', fromPartyId:vendorRepResult.partyId,
                    fromRoleTypeId:'Manager', toPartyId:vendorResult.partyId, toRoleTypeId:'VendorBillFrom',
                    fromDate:ec.user.nowTimestamp]).call()
        // worker
        workerResult = ec.service.sync().name("mantle.party.PartyServices.create#Account")
                .parameters([firstName:'Test', lastName:'Worker', emailAddress:'worker@test.com',
                    username:'worker', newPassword:'moqui1!', newPasswordVerify:'moqui1!', loginAfterCreate:'false']).call()
        Map workerRelResult = ec.service.sync().name("create#mantle.party.PartyRelationship")
                .parameters([relationshipTypeEnumId:'PrtAgent', fromPartyId:workerResult.partyId,
                    fromRoleTypeId:'Worker', toPartyId:vendorResult.partyId, toRoleTypeId:'VendorBillFrom',
                    fromDate:ec.user.nowTimestamp]).call()
        // Rate Amounts
        clientRateResult = ec.service.sync().name("create#mantle.humanres.rate.RateAmount")
                .parameters([rateTypeEnumId:'RatpStandard', ratePurposeEnumId:'RaprClient', timePeriodUomId:'TF_hr',
                    emplPositionClassId:'Programmer', fromDate:'2010-02-03 00:00:00', rateAmount:'60.00',
                    rateCurrencyUomId:'USD', partyId:workerResult.partyId]).call()
        vendorRateResult = ec.service.sync().name("create#mantle.humanres.rate.RateAmount")
                .parameters([rateTypeEnumId:'RatpStandard', ratePurposeEnumId:'RaprVendor', timePeriodUomId:'TF_hr',
                    emplPositionClassId:'Programmer', fromDate:'2010-02-03 00:00:00', rateAmount:'40.00',
                    rateCurrencyUomId:'USD', partyId:workerResult.partyId]).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.party.Party partyId="${vendorResult.partyId}" partyTypeEnumId="PtyOrganization"/>
            <mantle.party.Organization partyId="${vendorResult.partyId}" organizationName="Test Vendor"/>
            <mantle.party.PartyRole partyId="${vendorResult.partyId}" roleTypeId="OrgInternal"/>
            <mantle.party.PartyRole partyId="${vendorResult.partyId}" roleTypeId="VendorBillFrom"/>

            <mantle.party.contact.ContactMech contactMechId="${vendorCiResult.postalContactMechId}" contactMechTypeEnumId="CmtPostalAddress"/>
            <mantle.party.contact.PostalAddress contactMechId="${vendorCiResult.postalContactMechId}" address1="51 W. Center St." unitNumber="1234"
                city="Orem" stateProvinceGeoId="USA_UT" countryGeoId="USA" postalCode="84057" postalCodeExt="4605"/>
            <mantle.party.contact.PartyContactMech partyId="${vendorResult.partyId}" contactMechId="${vendorCiResult.postalContactMechId}"
                contactMechPurposeId="PostalPayment" fromDate="1383411600000"/>
            <mantle.party.contact.ContactMech contactMechId="${vendorCiResult.telecomContactMechId}" contactMechTypeEnumId="CmtTelecomNumber"/>
            <mantle.party.contact.PartyContactMech partyId="${vendorResult.partyId}" contactMechId="${vendorCiResult.telecomContactMechId}"
                contactMechPurposeId="PhonePayment" fromDate="1383411600000"/>
            <mantle.party.contact.TelecomNumber contactMechId="${vendorCiResult.telecomContactMechId}" countryCode="+1"
                areaCode="801" contactNumber="123-4567"/>
            <mantle.party.contact.ContactMech contactMechId="${vendorCiResult.emailContactMechId}"
                contactMechTypeEnumId="CmtEmailAddress" infoString="vendor.ar@test.com"/>
            <mantle.party.contact.PartyContactMech partyId="${vendorResult.partyId}"
                contactMechId="${vendorCiResult.emailContactMechId}" contactMechPurposeId="EmailPayment" fromDate="1383411600000"/>

            <mantle.ledger.transaction.GlJournal glJournalId="${vendorResult.partyId}Error"
                glJournalName="Error Journal for ${vendorResult.partyId}" organizationPartyId="${vendorResult.partyId}"/>
            <mantle.ledger.config.PartyAcctgPreference organizationPartyId="${vendorResult.partyId}"
                taxFormEnumId="TxfUsIrs1120" cogsMethodEnumId="CogsActualCost" baseCurrencyUomId="USD"
                invoiceSequenceEnumId="InvSqStandard" orderSequenceEnumId="OrdSqStandard"
                errorGlJournalId="${vendorResult.partyId}Error"/>
            <mantle.ledger.config.GlAccountTypeDefault glAccountTypeEnumId="ACCOUNTS_RECEIVABLE"
                organizationPartyId="${vendorResult.partyId}" glAccountId="120000"/>
            <mantle.ledger.config.GlAccountTypeDefault glAccountTypeEnumId="ACCOUNTS_PAYABLE"
                organizationPartyId="${vendorResult.partyId}" glAccountId="210000"/>
            <mantle.ledger.config.PaymentMethodTypeGlAccount paymentMethodTypeEnumId="PmtCompanyCheck"
                organizationPartyId="${vendorResult.partyId}" glAccountId="111100"/>
            <mantle.ledger.config.ItemTypeGlAccount itemTypeEnumId="ItemExpServLabor" organizationPartyId="${vendorResult.partyId}"
                glAccountId="649000"/>
            <mantle.ledger.config.ItemTypeGlAccount itemTypeEnumId="ItemExpTravAir" organizationPartyId="${vendorResult.partyId}"
                glAccountId="681000"/>
            <mantle.ledger.account.GlAccountOrganization glAccountId="120000" organizationPartyId="${vendorResult.partyId}"/>
            <mantle.ledger.account.GlAccountOrganization glAccountId="210000" organizationPartyId="${vendorResult.partyId}"/>
            <mantle.ledger.config.PaymentTypeGlAccount paymentTypeEnumId="PtInvoicePayment"
                organizationPartyId="${vendorResult.partyId}" isPayable="N" isApplied="Y" glAccountId="120000"/>
            <mantle.ledger.config.PaymentTypeGlAccount paymentTypeEnumId="PtInvoicePayment"
                organizationPartyId="${vendorResult.partyId}" isPayable="Y" isApplied="Y" glAccountId="210000"/>

            <mantle.party.Party partyId="${vendorRepResult.partyId}" partyTypeEnumId="PtyPerson" disabled="N"/>
            <mantle.party.Person partyId="${vendorRepResult.partyId}" firstName="Vendor" lastName="TestRep"/>
            <moqui.security.UserAccount userId="${vendorRepResult.userId}" username="vendor.rep" userFullName="Vendor TestRep"
                passwordHashType="SHA-256" passwordSetDate="1383411600000" disabled="N" requirePasswordChange="N"
                emailAddress="vendor.rep@test.com" partyId="${vendorRepResult.partyId}"/>
            <!-- the salt is generated randomly so can't easily validate the actual password or salt: currentPassword="32ce60c14d9e72c1fb17938ede30fe9de04390409cce7310743c2716a2c7bf89" passwordSalt="{.rqlPt8x" -->
            <mantle.party.contact.ContactMech contactMechId="${vendorRepResult.emailContactMechId}"
                contactMechTypeEnumId="CmtEmailAddress" infoString="vendor.rep@test.com"/>
            <mantle.party.contact.PartyContactMech partyId="${vendorRepResult.partyId}"
                contactMechId="${vendorRepResult.emailContactMechId}" contactMechPurposeId="EmailPrimary" fromDate="1383411600000"/>
            <mantle.party.PartyRelationship partyRelationshipId="${repRelResult.partyRelationshipId}"
                relationshipTypeEnumId="PrtRepresentative" fromPartyId="${vendorRepResult.partyId}" fromRoleTypeId="Manager"
                toPartyId="${vendorResult.partyId}" toRoleTypeId="VendorBillFrom" fromDate="1383411600000"/>

            <mantle.party.Party partyId="${workerResult.partyId}" partyTypeEnumId="PtyPerson" disabled="N"/>
            <mantle.party.Person partyId="${workerResult.partyId}" firstName="Test" lastName="Worker"/>
            <moqui.security.UserAccount userId="${workerResult.userId}" username="worker" userFullName="Test Worker"
                passwordHashType="SHA-256" passwordSetDate="1383411600000" disabled="N" requirePasswordChange="N"
                emailAddress="worker@test.com" partyId="${workerResult.partyId}"/>
            <!-- the salt is generated randomly so can't easily validate the actual password or salt: currentPassword="32ce60c14d9e72c1fb17938ede30fe9de04390409cce7310743c2716a2c7bf89" passwordSalt="{.rqlPt8x" -->
            <mantle.party.contact.ContactMech contactMechId="${workerResult.emailContactMechId}"
                contactMechTypeEnumId="CmtEmailAddress" infoString="worker@test.com"/>
            <mantle.party.contact.PartyContactMech partyId="${workerResult.partyId}"
                contactMechId="${workerResult.emailContactMechId}" contactMechPurposeId="EmailPrimary" fromDate="1383411600000"/>
            <mantle.party.PartyRelationship partyRelationshipId="${workerRelResult.partyRelationshipId}"
                relationshipTypeEnumId="PrtAgent" fromPartyId="${workerResult.partyId}" fromRoleTypeId="Worker"
                toPartyId="${vendorResult.partyId}" toRoleTypeId="VendorBillFrom" fromDate="1383411600000"/>

            <mantle.humanres.rate.RateAmount rateAmountId="${clientRateResult.rateAmountId}" rateTypeEnumId="RatpStandard"
                ratePurposeEnumId="RaprClient" timePeriodUomId="TF_hr" partyId="${workerResult.partyId}"
                emplPositionClassId="Programmer" fromDate="2010-02-03 00:00:00" rateAmount="60.00" rateCurrencyUomId="USD"/>
            <mantle.humanres.rate.RateAmount rateAmountId="${vendorRateResult.rateAmountId}" rateTypeEnumId="RatpStandard"
                ratePurposeEnumId="RaprVendor" timePeriodUomId="TF_hr" partyId="${workerResult.partyId}"
                emplPositionClassId="Programmer" fromDate="2010-02-03 00:00:00" rateAmount="40.00" rateCurrencyUomId="USD"/>
        </entity-facade-xml>""").check()
        logger.info("TEST create Vendor data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create Client"() {
        when:
        clientResult = ec.service.sync().name("mantle.party.PartyServices.create#Organization")
                .parameters([roleTypeId:'CustomerBillTo', organizationName:'Test Client']).call()
        Map clientCiResult = ec.service.sync().name("mantle.party.ContactServices.store#PartyContactInfo")
                .parameters([partyId:clientResult.partyId, postalContactMechPurposeId:'PostalBilling',
                telecomContactMechPurposeId:'PhoneBilling', emailContactMechPurposeId:'EmailBilling', countryGeoId:'USA',
                address1:'1350 E. Flamingo Rd.', unitNumber:'1234', city:'Las Vegas', stateProvinceGeoId:'USA_NV',
                postalCode:'89119', postalCodeExt:'5263', countryCode:'+1', areaCode:'702', contactNumber:'123-4567',
                emailAddress:'client.ap@test.com']).call()

        Map clientRepResult = ec.service.sync().name("mantle.party.PartyServices.create#Account")
                .parameters([firstName:'Client', lastName:'TestRep', emailAddress:'client.rep@test.com',
                username:'client.rep', newPassword:'moqui1!', newPasswordVerify:'moqui1!', loginAfterCreate:'false']).call()
        Map repRelResult = ec.service.sync().name("create#mantle.party.PartyRelationship")
                .parameters([relationshipTypeEnumId:'PrtRepresentative', fromPartyId:clientRepResult.partyId,
                fromRoleTypeId:'ClientBilling', toPartyId:clientResult.partyId, toRoleTypeId:'CustomerBillTo',
                fromDate:ec.user.nowTimestamp]).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.party.Party partyId="${clientResult.partyId}" partyTypeEnumId="PtyOrganization"/>
            <mantle.party.Organization partyId="${clientResult.partyId}" organizationName="Test Client"/>
            <mantle.party.PartyRole partyId="${clientResult.partyId}" roleTypeId="CustomerBillTo"/>

            <mantle.party.contact.ContactMech contactMechId="${clientCiResult.postalContactMechId}" contactMechTypeEnumId="CmtPostalAddress"/>
            <mantle.party.contact.PostalAddress contactMechId="${clientCiResult.postalContactMechId}"
                address1="1350 E. Flamingo Rd." unitNumber="1234" city="Las Vegas" stateProvinceGeoId="USA_NV"
                countryGeoId="USA" postalCode="89119" postalCodeExt="5263"/>
            <mantle.party.contact.PartyContactMech partyId="${clientResult.partyId}" contactMechId="${clientCiResult.postalContactMechId}"
                contactMechPurposeId="PostalBilling" fromDate="1383411600000"/>
            <mantle.party.contact.ContactMech contactMechId="${clientCiResult.telecomContactMechId}" contactMechTypeEnumId="CmtTelecomNumber"/>
            <mantle.party.contact.PartyContactMech partyId="${clientResult.partyId}" contactMechId="${clientCiResult.telecomContactMechId}"
                contactMechPurposeId="PhoneBilling" fromDate="1383411600000"/>
            <mantle.party.contact.TelecomNumber contactMechId="${clientCiResult.telecomContactMechId}" countryCode="+1"
                areaCode="702" contactNumber="123-4567"/>
            <mantle.party.contact.ContactMech contactMechId="${clientCiResult.emailContactMechId}"
                contactMechTypeEnumId="CmtEmailAddress" infoString="client.ap@test.com"/>
            <mantle.party.contact.PartyContactMech partyId="${clientResult.partyId}"
                contactMechId="${clientCiResult.emailContactMechId}" contactMechPurposeId="EmailBilling" fromDate="1383411600000"/>

            <mantle.party.Party partyId="${clientRepResult.partyId}" partyTypeEnumId="PtyPerson" disabled="N"/>
            <mantle.party.Person partyId="${clientRepResult.partyId}" firstName="Client" lastName="TestRep"/>
            <moqui.security.UserAccount userId="${clientRepResult.userId}" username="client.rep" userFullName="Client TestRep"
                passwordHashType="SHA-256" passwordSetDate="1383411600000" disabled="N" requirePasswordChange="N"
                emailAddress="client.rep@test.com" partyId="${clientRepResult.partyId}"/>
            <!-- the salt is generated randomly so can't easily validate the actual password or salt: currentPassword="32ce60c14d9e72c1fb17938ede30fe9de04390409cce7310743c2716a2c7bf89" passwordSalt="{.rqlPt8x" -->
            <mantle.party.contact.ContactMech contactMechId="${clientRepResult.emailContactMechId}"
                contactMechTypeEnumId="CmtEmailAddress" infoString="client.rep@test.com"/>
            <mantle.party.contact.PartyContactMech partyId="${clientRepResult.partyId}"
                contactMechId="${clientRepResult.emailContactMechId}" contactMechPurposeId="EmailPrimary" fromDate="1383411600000"/>
            <mantle.party.PartyRelationship partyRelationshipId="${repRelResult.partyRelationshipId}"
                relationshipTypeEnumId="PrtRepresentative" fromPartyId="${clientRepResult.partyId}"
                fromRoleTypeId="ClientBilling" toPartyId="${clientResult.partyId}" toRoleTypeId="CustomerBillTo" fromDate="1383411600000"/>
        </entity-facade-xml>""").check()
        logger.info("TEST create Vendor data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create TEST Project"() {
        when:
        ec.service.sync().name("mantle.work.ProjectServices.create#Project")
                .parameters([workEffortId:'TEST', workEffortName:'Test Proj', clientPartyId:clientResult.partyId, vendorPartyId:vendorResult.partyId])
                .call()
        ec.service.sync().name("mantle.work.ProjectServices.update#Project")
                .parameters([workEffortId:'TEST', workEffortName:'Test Project', statusId:'WeInProgress'])
                .call()
        // assign Joe Developer to TEST project as Programmer (necessary for determining RateAmount, etc)
        ec.service.sync().name("create#mantle.work.effort.WorkEffortParty")
                .parameters([workEffortId:'TEST', partyId:workerResult.partyId, roleTypeId:'Worker', emplPositionClassId:'Programmer',
                fromDate:'2013-11-01', statusId:'PRTYASGN_ASSIGNED']).call()

        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.work.effort.WorkEffort workEffortId="TEST" workEffortTypeEnumId="WetProject" statusId="WeInProgress" workEffortName="Test Project"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST" partyId="EX_JOHN_DOE" roleTypeId="Manager" fromDate="1383411600000" statusId="PRTYASGN_ASSIGNED"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST" partyId="${clientResult.partyId}" roleTypeId="CustomerBillTo" fromDate="1383411600000"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST" partyId="${vendorResult.partyId}" roleTypeId="VendorBillFrom" fromDate="1383411600000"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST" partyId="${workerResult.partyId}" roleTypeId="Worker"
                fromDate="1383282000000" statusId="PRTYASGN_ASSIGNED" emplPositionClassId="Programmer"/>
            <!-- how to handle seqId?
            <moqui.entity.EntityAuditLog auditHistorySeqId="100151" changedEntityName="mantle.work.effort.WorkEffortParty" changedFieldName="statusId" pkPrimaryValue="TEST" pkSecondaryValue="EX_JOHN_DOE" pkRestCombinedValue="roleTypeId=Manager,fromDate=2013-11-02 12:00:00.0" newValueText="PRTYASGN_ASSIGNED" changedByUserId="EX_JOHN_DOE"/>
            <moqui.entity.EntityAuditLog auditHistorySeqId="100150" changedEntityName="mantle.work.effort.WorkEffort" changedFieldName="statusId" pkPrimaryValue="TEST" newValueText="WeInPlanning" changedByUserId="EX_JOHN_DOE"/>
            <moqui.entity.EntityAuditLog auditHistorySeqId="100152" changedEntityName="mantle.work.effort.WorkEffort" changedFieldName="statusId" pkPrimaryValue="TEST" oldValueText="WeInPlanning" newValueText="WeInProgress" changedByUserId="EX_JOHN_DOE"/>
            -->
            </entity-facade-xml>""").check()
        logger.info("TEST Project data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create TEST Milestones"() {
        when:
        ec.service.sync().name("mantle.work.ProjectServices.create#Milestone")
                .parameters([rootWorkEffortId:'TEST', workEffortId:'TEST-MS-01', workEffortName:'Test Milestone 1',
                estimatedStartDate:'2013-11-01', estimatedCompletionDate:'2013-11-30', statusId:'WeInProgress'])
                .call()
        ec.service.sync().name("mantle.work.ProjectServices.create#Milestone")
                .parameters([rootWorkEffortId:'TEST', workEffortId:'TEST-MS-02', workEffortName:'Test Milestone 2',
                estimatedStartDate:'2013-12-01', estimatedCompletionDate:'2013-12-31', statusId:'WeApproved'])
                .call()

        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.work.effort.WorkEffort workEffortId="TEST-MS-01" rootWorkEffortId="TEST" workEffortTypeEnumId="WetMilestone"
                statusId="WeInProgress" workEffortName="Test Milestone 1" estimatedStartDate="2013-11-01 00:00:00.0" estimatedCompletionDate="2013-11-30 00:00:00.0"/>
            <mantle.work.effort.WorkEffort workEffortId="TEST-MS-02" rootWorkEffortId="TEST" workEffortTypeEnumId="WetMilestone"
                statusId="WeApproved" workEffortName="Test Milestone 2" estimatedStartDate="2013-12-01 00:00:00.0" estimatedCompletionDate="2013-12-31 00:00:00.0"/>
            <!-- how to handle seqId?
            <moqui.entity.EntityAuditLog auditHistorySeqId="100153" changedEntityName="mantle.work.effort.WorkEffort" changedFieldName="statusId" pkPrimaryValue="TEST-MS-01" newValueText="WeInProgress" changedByUserId="EX_JOHN_DOE"/>
            <moqui.entity.EntityAuditLog auditHistorySeqId="100154" changedEntityName="mantle.work.effort.WorkEffort" changedFieldName="statusId" pkPrimaryValue="TEST-MS-02" newValueText="WeApproved" changedByUserId="EX_JOHN_DOE"/>
            -->
            </entity-facade-xml>""").check()
        logger.info("TEST Milestones data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create TEST Project Tasks"() {
        when:
        ec.service.sync().name("mantle.work.TaskServices.create#Task")
                .parameters([rootWorkEffortId:'TEST', parentWorkEffortId:null, workEffortId:'TEST-001', milestoneWorkEffortId:'TEST-MS-01',
                workEffortName:'Test Task 1', estimatedCompletionDate:'2013-11-15', statusId:'WeApproved',
                assignToPartyId:workerResult.partyId, priority:3, purposeEnumId:'WepTask', estimatedWorkTime:10,
                description:'Will be really great when it\'s done'])
                .call()
        ec.service.sync().name("mantle.work.TaskServices.create#Task")
                .parameters([rootWorkEffortId:'TEST', parentWorkEffortId:'TEST-001', workEffortId:'TEST-001A', milestoneWorkEffortId:'TEST-MS-01',
                workEffortName:'Test Task 1A', estimatedCompletionDate:'2013-11-15', statusId:'WeInPlanning',
                assignToPartyId:workerResult.partyId, priority:4, purposeEnumId:'WepNewFeature', estimatedWorkTime:2,
                description:'One piece of the puzzle'])
                .call()
        ec.service.sync().name("mantle.work.TaskServices.create#Task")
                .parameters([rootWorkEffortId:'TEST', parentWorkEffortId:'TEST-001', workEffortId:'TEST-001B', milestoneWorkEffortId:'TEST-MS-01',
                workEffortName:'Test Task 1B', estimatedCompletionDate:'2013-11-15', statusId:'WeApproved',
                assignToPartyId:workerResult.partyId, priority:4, purposeEnumId:'WepFix', estimatedWorkTime:2,
                description:'Broken piece of the puzzle'])
                .call()
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.work.effort.WorkEffort workEffortId="TEST-001" rootWorkEffortId="TEST" workEffortTypeEnumId="WetTask"
                purposeEnumId="WepTask" resolutionEnumId="WerUnresolved" statusId="WeApproved" priority="3"
                workEffortName="Test Task 1" description="Will be really great when it's done"
                estimatedCompletionDate="1384495200000" estimatedWorkTime="10" remainingWorkTime="10" timeUomId="TF_hr"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST-001" partyId="${workerResult.partyId}" roleTypeId="Worker"
                fromDate="1383411600000" statusId="PRTYASGN_ASSIGNED"/>
            <mantle.work.effort.WorkEffortAssoc workEffortId="TEST-MS-01" toWorkEffortId="TEST-001"
                workEffortAssocTypeEnumId="WeatMilestone" fromDate="1383411600000"/>

            <mantle.work.effort.WorkEffort workEffortId="TEST-001A" parentWorkEffortId="TEST-001" rootWorkEffortId="TEST"
                workEffortTypeEnumId="WetTask" purposeEnumId="WepNewFeature" resolutionEnumId="WerUnresolved"
                statusId="WeInPlanning" priority="4" workEffortName="Test Task 1A" description="One piece of the puzzle"
                estimatedCompletionDate="1384495200000" estimatedWorkTime="2" remainingWorkTime="2" timeUomId="TF_hr"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST-001A" partyId="${workerResult.partyId}" roleTypeId="Worker"
                fromDate="1383411600000" statusId="PRTYASGN_ASSIGNED"/>
            <mantle.work.effort.WorkEffortAssoc workEffortId="TEST-MS-01" toWorkEffortId="TEST-001A"
                workEffortAssocTypeEnumId="WeatMilestone" fromDate="1383411600000"/>

            <mantle.work.effort.WorkEffort workEffortId="TEST-001B" parentWorkEffortId="TEST-001" rootWorkEffortId="TEST"
                workEffortTypeEnumId="WetTask" purposeEnumId="WepFix" resolutionEnumId="WerUnresolved" statusId="WeApproved"
                priority="4" workEffortName="Test Task 1B" description="Broken piece of the puzzle"
                estimatedCompletionDate="1384495200000" estimatedWorkTime="2" remainingWorkTime="2" timeUomId="TF_hr"/>
            <mantle.work.effort.WorkEffortParty workEffortId="TEST-001B" partyId="${workerResult.partyId}" roleTypeId="Worker"
                fromDate="1383411600000" statusId="PRTYASGN_ASSIGNED"/>
            <mantle.work.effort.WorkEffortAssoc workEffortId="TEST-MS-01" toWorkEffortId="TEST-001B"
                workEffortAssocTypeEnumId="WeatMilestone" fromDate="1383411600000"/>
            </entity-facade-xml>""").check()
        logger.info("TEST Milestones data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "record TimeEntries and complete Tasks"() {
        when:
        // get tasks In Progress
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001', statusId:'WeInProgress']).call()
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001A', statusId:'WeInProgress']).call()
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001B', statusId:'WeInProgress']).call()
        // plain hours, nothing else
        ec.service.sync().name("mantle.work.TaskServices.add#TaskTime")
                .parameters([workEffortId:'TEST-001', partyId:workerResult.partyId, rateTypeEnumId:'RatpStandard', remainingWorkTime:3,
                    hours:6, fromDate:null, thruDate:null, breakHours:null]).call()
        // hours and break, no from/thru dates (determined automatically, thru based on now and from based on hours+break)
        ec.service.sync().name("mantle.work.TaskServices.add#TaskTime")
                .parameters([workEffortId:'TEST-001A', partyId:workerResult.partyId, rateTypeEnumId:'RatpStandard', remainingWorkTime:1,
                    hours:1.5, fromDate:null, thruDate:null, breakHours:0.5]).call()
        // break and from/thru dates, hours determined automatically
        ec.service.sync().name("mantle.work.TaskServices.add#TaskTime")
                .parameters([workEffortId:'TEST-001B', partyId:workerResult.partyId, rateTypeEnumId:'RatpStandard', remainingWorkTime:0.5,
                    hours:null, fromDate:"2013-11-03 12:00:00", thruDate:"2013-11-03 15:00:00", breakHours:1]).call()
        // complete tasks
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001', statusId:'WeComplete', resolutionEnumId:'WerCompleted']).call()
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001A', statusId:'WeComplete', resolutionEnumId:'WerCompleted']).call()
        ec.service.sync().name("mantle.work.TaskServices.update#Task").parameters([workEffortId:'TEST-001B', statusId:'WeComplete', resolutionEnumId:'WerCompleted']).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.work.effort.WorkEffort workEffortId="TEST-001" resolutionEnumId="WerCompleted" statusId="WeComplete"
                estimatedWorkTime="10" remainingWorkTime="3" actualWorkTime="6"/>
            <mantle.work.time.TimeEntry timeEntryId="100000" partyId="${workerResult.partyId}" rateTypeEnumId="RatpStandard"
                rateAmountId="${clientRateResult.rateAmountId}" vendorRateAmountId="${vendorRateResult.rateAmountId}"
                fromDate="1383390000000" thruDate="1383411600000" hours="6" workEffortId="TEST-001"/>
            <mantle.work.effort.WorkEffort workEffortId="TEST-001A" resolutionEnumId="WerCompleted" statusId="WeComplete"
                estimatedWorkTime="2" remainingWorkTime="1" actualWorkTime="1.5"/>
            <mantle.work.time.TimeEntry timeEntryId="100001" partyId="${workerResult.partyId}" rateTypeEnumId="RatpStandard"
                rateAmountId="${clientRateResult.rateAmountId}" vendorRateAmountId="${vendorRateResult.rateAmountId}"
                fromDate="1383404400000" thruDate="1383411600000" hours="1.5" breakHours="0.5" workEffortId="TEST-001A"/>
            <mantle.work.effort.WorkEffort workEffortId="TEST-001B" resolutionEnumId="WerCompleted" statusId="WeComplete"
                estimatedWorkTime="2" remainingWorkTime="0.5" actualWorkTime="2"/>
            <mantle.work.time.TimeEntry timeEntryId="100002" partyId="${workerResult.partyId}" rateTypeEnumId="RatpStandard"
                rateAmountId="${clientRateResult.rateAmountId}" vendorRateAmountId="${vendorRateResult.rateAmountId}"
                fromDate="1383501600000" thruDate="1383512400000" hours="2" breakHours="1" workEffortId="TEST-001B"/>
        </entity-facade-xml>""").check()
        logger.info("record TimeEntries and complete Tasks data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create Request and Task for Request"() {
        when:
        Map createReqResult = ec.service.sync().name("mantle.request.RequestServices.create#Request")
                .parameters([clientPartyId:clientResult.partyId, assignToPartyId:workerResult.partyId, requestName:'Test Request 1',
                    description:'Description of Test Request 1', priority:7, requestTypeEnumId:'RqtSupport',
                    statusId:'ReqSubmitted', responseRequiredDate:'2013-11-15 15:00:00']).call()
        ec.service.sync().name("mantle.request.RequestServices.update#Request")
                .parameters([requestId:createReqResult.requestId, statusId:'ReqReviewed']).call()
        ec.service.sync().name("mantle.request.RequestServices.update#Request")
                .parameters([requestId:createReqResult.requestId, statusId:'ReqCompleted']).call()

        Map createReqTskResult = ec.service.sync().name("mantle.work.TaskServices.create#Task")
                .parameters([rootWorkEffortId:'TEST', workEffortName:'Test Request 1 Task',
                    estimatedCompletionDate:'2013-11-15', statusId:'WeApproved', assignToPartyId:workerResult.partyId,
                    priority:7, purposeEnumId:'WepTask', estimatedWorkTime:2, description:'']).call()
        ec.service.sync().name("create#mantle.request.RequestWorkEffort")
                .parameters([workEffortId:createReqTskResult.workEffortId, requestId:createReqResult.requestId]).call()
        ec.service.sync().name("mantle.work.TaskServices.update#Task")
                .parameters([workEffortId:createReqTskResult.workEffortId, statusId:'WeComplete',
                    resolutionEnumId:'WerCompleted']).call()

        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.request.Request requestId="${createReqResult.requestId}" requestTypeEnumId="RqtSupport"
                statusId="ReqCompleted" requestName="Test Request 1" description="Description of Test Request 1" priority="7"
                responseRequiredDate="1384549200000" requestResolutionEnumId="RrUnresolved" filedByPartyId="EX_JOHN_DOE"/>
            <mantle.request.RequestWorkEffort requestId="${createReqResult.requestId}"
                workEffortId="${createReqTskResult.workEffortId}" lastUpdatedStamp="1382060813771"/>
            <mantle.request.RequestParty requestId="${createReqResult.requestId}" partyId="${workerResult.partyId}"
                roleTypeId="Worker" fromDate="1383411600000"/>
            <mantle.request.RequestParty requestId="${createReqResult.requestId}" partyId="${clientResult.partyId}"
                roleTypeId="CustomerBillTo" fromDate="1383411600000"/>
            <mantle.work.effort.WorkEffort workEffortId="${createReqTskResult.workEffortId}" rootWorkEffortId="TEST"
                workEffortTypeEnumId="WetTask" purposeEnumId="WepTask" resolutionEnumId="WerCompleted" statusId="WeComplete"
                priority="7" workEffortName="Test Request 1 Task" estimatedCompletionDate="1384495200000"
                estimatedWorkTime="2" remainingWorkTime="2" timeUomId="TF_hr"/>
            <mantle.work.effort.WorkEffortParty workEffortId="${createReqTskResult.workEffortId}" partyId="${workerResult.partyId}"
                roleTypeId="Worker" fromDate="1383411600000" statusId="PRTYASGN_ASSIGNED"/>
        </entity-facade-xml>""").check()
        logger.info("create Request and Task for Request data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "create Worker Time and Expense Invoice and record Payment"() {
        when:
        // create expense invoices and add items
        expInvResult = ec.service.sync().name("mantle.account.InvoiceServices.create#ProjectExpenseInvoice")
                .parameters([workEffortId:'TEST', fromPartyId:workerResult.partyId]).call()
        ec.service.sync().name("create#mantle.account.invoice.InvoiceItem")
                .parameters([invoiceId:expInvResult.invoiceId, itemTypeEnumId:'ItemExpTravAir',
                    description:'United SFO-LAX', itemDate:'2013-11-02', quantity:1, amount:345.67]).call()
        ec.service.sync().name("create#mantle.account.invoice.InvoiceItem")
                .parameters([invoiceId:expInvResult.invoiceId, itemTypeEnumId:'ItemExpTravLodging',
                    description:'Fleabag Inn 2 nights', itemDate:'2013-11-04', quantity:1, amount:123.45]).call()
        // add worker/vendor time to the expense invoice
        ec.service.sync().name("mantle.account.InvoiceServices.create#ProjectInvoiceItems")
                .parameters([invoiceId:expInvResult.invoiceId, workerPartyId:workerResult.partyId,
                    ratePurposeEnumId:'RaprVendor', workEffortId:'TEST', thruDate:'2013-11-10 12:00:00']).call()
        // "submit" the expense/time invoice
        ec.service.sync().name("update#mantle.account.invoice.Invoice")
                .parameters([invoiceId:expInvResult.invoiceId, statusId:'InvoiceReceived']).call()

        // pay the invoice (345.67 + 123.45 + (9.5 * 40) = 849.12)
        Map expPmtResult = ec.service.sync().name("mantle.account.PaymentServices.create#InvoicePayment")
                .parameters([invoiceId:expInvResult.invoiceId, statusId:'PmntDelivered', amount:'849.12',
                    paymentMethodTypeEnumId:'PmtCompanyCheck', effectiveDate:'2013-11-10 12:00:00',
                    paymentRefNum:'1234', comments:'Delivered by Fedex']).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.account.invoice.Invoice invoiceId="${expInvResult.invoiceId}" invoiceTypeEnumId="InvoiceSales" fromPartyId="${workerResult.partyId}"
                toPartyId="${vendorResult.partyId}" statusId="InvoicePmtSent" invoiceDate="1383404400000" currencyUomId="USD"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="01" itemTypeEnumId="ItemExpTravAir"
                quantity="1" amount="345.67" description="United SFO-LAX" itemDate="1383368400000"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="02" itemTypeEnumId="ItemExpTravLodging"
                quantity="1" amount="123.45" description="Fleabag Inn 2 nights" itemDate="1383544800000"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="03" itemTypeEnumId="ItemExpServLabor"
                quantity="6" amount="40" itemDate="1383390000000"/>
            <mantle.work.time.TimeEntry timeEntryId="100000" vendorInvoiceId="100000" vendorInvoiceItemSeqId="03"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="04" itemTypeEnumId="ItemExpServLabor"
                quantity="1.5" amount="40" itemDate="1383404400000"/>
            <mantle.work.time.TimeEntry timeEntryId="100001" vendorInvoiceId="100000" vendorInvoiceItemSeqId="04"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="05" itemTypeEnumId="ItemExpServLabor"
                quantity="2" amount="40" itemDate="1383501600000"/>
            <mantle.work.time.TimeEntry timeEntryId="100002" vendorInvoiceId="100000" vendorInvoiceItemSeqId="05"/>
            <mantle.ledger.transaction.AcctgTrans acctgTransId="100000" acctgTransTypeEnumId="AttPurchaseInvoice"
                organizationPartyId="${vendorResult.partyId}" transactionDate="1383411600000" isPosted="Y" postedDate="1383411600000"
                glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="${workerResult.partyId}" invoiceId="${expInvResult.invoiceId}"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="01" debitCreditFlag="D"
                amount="345.67" glAccountId="681000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="01"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="02" debitCreditFlag="D"
                amount="123.45" glAccountId="681000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="02"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="03" debitCreditFlag="D"
                amount="240" glAccountId="649000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="03"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="04" debitCreditFlag="D"
                amount="60" glAccountId="649000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="04"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="05" debitCreditFlag="D"
                amount="80" glAccountId="649000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="05"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100000" acctgTransEntrySeqId="06" debitCreditFlag="C"
                amount="849.12" glAccountTypeEnumId="ACCOUNTS_PAYABLE" glAccountId="210000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
            <mantle.work.effort.WorkEffortInvoice invoiceId="${expInvResult.invoiceId}" workEffortId="TEST"/>

            <mantle.account.payment.Payment paymentId="${expPmtResult.paymentId}" paymentTypeEnumId="PtInvoicePayment"
                fromPartyId="${vendorResult.partyId}" toPartyId="${workerResult.partyId}" paymentMethodTypeEnumId="PmtCompanyCheck"
                statusId="PmntDelivered" effectiveDate="1384106400000" paymentRefNum="1234" comments="Delivered by Fedex"
                amount="849.12" amountUomId="USD"/>
            <mantle.ledger.transaction.AcctgTrans acctgTransId="100001" acctgTransTypeEnumId="AttOutgoingPayment"
                organizationPartyId="${vendorResult.partyId}" transactionDate="1383411600000" isPosted="Y"
                postedDate="1383411600000" glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="${workerResult.partyId}"
                paymentId="${expPmtResult.paymentId}"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100001" acctgTransEntrySeqId="01" debitCreditFlag="D"
                amount="849.12" glAccountId="210000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100001" acctgTransEntrySeqId="02" debitCreditFlag="C"
                amount="849.12" glAccountId="111100" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>

            <mantle.account.payment.PaymentApplication paymentApplicationId="${expPmtResult.paymentApplicationId}"
                paymentId="${expPmtResult.paymentId}" invoiceId="${expInvResult.invoiceId}" amountApplied="849.12"
                appliedDate="1383411600000"/>
        </entity-facade-xml>""").check()
        logger.info("create Worker Time and Expense Invoice and record Payment data check results: " + dataCheckErrors)
        then:
        dataCheckErrors.size() == 0
    }

    def "create Client Time and Expense Invoice and Finalize"() {
        when:
        clientInvResult = ec.service.sync().name("mantle.account.InvoiceServices.create#ProjectInvoiceItems")
                .parameters([ratePurposeEnumId:'RaprClient', workEffortId:'TEST', thruDate:'2013-11-11 12:00:00']).call()
        // this will trigger the GL posting
        ec.service.sync().name("update#mantle.account.invoice.Invoice")
                .parameters([invoiceId:clientInvResult.invoiceId, statusId:'InvoiceFinalized']).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.account.invoice.Invoice invoiceId="${clientInvResult.invoiceId}" invoiceTypeEnumId="InvoiceSales"
                fromPartyId="${vendorResult.partyId}" toPartyId="${clientResult.partyId}" statusId="InvoiceFinalized" invoiceDate="1383411600000"
                description="Invoice for projectTest Project [TEST] " currencyUomId="USD"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="01"
                itemTypeEnumId="ItemTimeEntry" quantity="6" amount="60" itemDate="1383390000000"/>
            <mantle.work.time.TimeEntry timeEntryId="100000" invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="01"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="02"
                itemTypeEnumId="ItemTimeEntry" quantity="1.5" amount="60" itemDate="1383404400000"/>
            <mantle.work.time.TimeEntry timeEntryId="100001" invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="02"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="03"
                itemTypeEnumId="ItemTimeEntry" quantity="2" amount="60" itemDate="1383501600000"/>
            <mantle.work.time.TimeEntry timeEntryId="100002" invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="03"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="04"
                itemTypeEnumId="ItemExpTravAir" quantity="1" amount="345.67" description="United SFO-LAX" itemDate="1383368400000"/>
            <mantle.account.invoice.InvoiceItemAssoc invoiceItemAssocId="100000" invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="01"
                toInvoiceId="100001" toInvoiceItemSeqId="04" invoiceItemAssocTypeEnumId="IiatBillThrough" quantity="1" amount="345.67"/>
            <mantle.account.invoice.InvoiceItem invoiceId="${clientInvResult.invoiceId}" invoiceItemSeqId="05"
                itemTypeEnumId="ItemExpTravLodging" quantity="1" amount="123.45" description="Fleabag Inn 2 nights" itemDate="1383544800000"/>
            <mantle.account.invoice.InvoiceItemAssoc invoiceItemAssocId="100001" invoiceId="${expInvResult.invoiceId}" invoiceItemSeqId="02"
                toInvoiceId="100001" toInvoiceItemSeqId="05" invoiceItemAssocTypeEnumId="IiatBillThrough" quantity="1" amount="123.45"/>

            <mantle.ledger.transaction.AcctgTrans acctgTransId="100002" acctgTransTypeEnumId="AttSalesInvoice"
                organizationPartyId="${vendorResult.partyId}" transactionDate="1383411600000" isPosted="Y" postedDate="1383411600000"
                glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="${clientResult.partyId}" invoiceId="${clientInvResult.invoiceId}"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="01" debitCreditFlag="C"
                amount="360" glAccountId="402000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="01"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="02" debitCreditFlag="C"
                amount="90" glAccountId="402000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="02"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="03" debitCreditFlag="C"
                amount="120" glAccountId="402000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="03"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="04" debitCreditFlag="C"
                amount="345.67" glAccountId="681000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="04"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="05" debitCreditFlag="C"
                amount="123.45" glAccountId="681000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N" invoiceItemSeqId="05"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100002" acctgTransEntrySeqId="06" debitCreditFlag="D"
                amount="1,039.12" glAccountTypeEnumId="ACCOUNTS_RECEIVABLE" glAccountId="120000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
        </entity-facade-xml>""").check()
        logger.info("create Client Time and Expense Invoice and Finalize data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }

    def "record Payment for Client Time and Expense Invoice"() {
        when:
        Map clientPmtResult = ec.service.sync().name("mantle.account.PaymentServices.create#InvoicePayment")
                .parameters([invoiceId:clientInvResult.invoiceId, statusId:'PmntDelivered', amount:1039.12,
                    paymentMethodTypeEnumId:'PmtCompanyCheck', effectiveDate:'2013-11-12 12:00:00', paymentRefNum:'54321']).call()

        // NOTE: this has sequenced IDs so is sensitive to run order!
        List<String> dataCheckErrors = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.account.invoice.Invoice invoiceId="${clientInvResult.invoiceId}" statusId="InvoicePmtRecvd"/>
            <mantle.account.payment.Payment paymentId="${clientPmtResult.paymentId}" paymentTypeEnumId="PtInvoicePayment"
                fromPartyId="${clientResult.partyId}" toPartyId="${vendorResult.partyId}" paymentMethodTypeEnumId="PmtCompanyCheck"
                statusId="PmntDelivered" effectiveDate="1384279200000" paymentRefNum="54321" amount="1,039.12" amountUomId="USD"/>
            <mantle.account.payment.PaymentApplication paymentApplicationId="100001" paymentId="${clientPmtResult.paymentId}"
                invoiceId="${clientInvResult.invoiceId}" amountApplied="1,039.12" appliedDate="1383411600000"/>
            <mantle.ledger.transaction.AcctgTrans acctgTransId="100003" acctgTransTypeEnumId="AttIncomingPayment"
                organizationPartyId="${vendorResult.partyId}" transactionDate="1383411600000" isPosted="Y" postedDate="1383411600000"
                glFiscalTypeEnumId="GLFT_ACTUAL" amountUomId="USD" otherPartyId="${clientResult.partyId}" paymentId="${clientPmtResult.paymentId}"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100003" acctgTransEntrySeqId="01" debitCreditFlag="C"
                amount="1,039.12" glAccountId="120000" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
            <mantle.ledger.transaction.AcctgTransEntry acctgTransId="100003" acctgTransEntrySeqId="02" debitCreditFlag="D"
                amount="1,039.12" glAccountId="111100" reconcileStatusId="AES_NOT_RECONCILED" isSummary="N"/>
        </entity-facade-xml>""").check()
        logger.info("record Payment for Client Time and Expense Invoice data check results: " + dataCheckErrors)

        then:
        dataCheckErrors.size() == 0
    }
}