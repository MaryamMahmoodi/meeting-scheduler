package com.demo.meetingscheduler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guards the hexagonal structure: dependencies point inwards, towards the domain. */
@AnalyzeClasses(packages = "com.demo.meetingscheduler", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule layersOnlyDependInwards = layeredArchitecture().consideringOnlyDependenciesInLayers()
			.layer("Domain").definedBy("com.demo.meetingscheduler.domain..")
			.layer("Application").definedBy("com.demo.meetingscheduler.application..")
			.layer("Web adapter").definedBy("com.demo.meetingscheduler.adapter.in..")
			.layer("Persistence adapter").definedBy("com.demo.meetingscheduler.adapter.outbound..")
			.layer("Config").definedBy("com.demo.meetingscheduler.config..")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Web adapter", "Persistence adapter")
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Web adapter", "Persistence adapter", "Config")
			.whereLayer("Web adapter").mayNotBeAccessedByAnyLayer()
			.whereLayer("Persistence adapter").mayNotBeAccessedByAnyLayer()
			.whereLayer("Config").mayNotBeAccessedByAnyLayer();

	@ArchTest
	static final ArchRule domainIsFreeOfFrameworks = noClasses().that().resideInAPackage("com.demo.meetingscheduler.domain..")
			.should().dependOnClassesThat().resideOutsideOfPackages("com.demo.meetingscheduler.domain..", "java..");

	/** Declarative transactions are the one piece of Spring the use cases are allowed to know about. */
	@ArchTest
	static final ArchRule applicationOnlyKnowsTheDomainAndTransactionBoundaries = noClasses().that()
			.resideInAPackage("com.demo.meetingscheduler.application..").should().dependOnClassesThat()
			.resideOutsideOfPackages("com.demo.meetingscheduler.domain..", "com.demo.meetingscheduler.application..", "java..",
					"org.springframework.transaction.annotation..");

	@ArchTest
	static final ArchRule webAdapterOnlyCallsUseCases = noClasses().that()
			.resideInAPackage("com.demo.meetingscheduler.adapter.in..").should().dependOnClassesThat()
			.resideInAnyPackage("com.demo.meetingscheduler.application.service..", "com.demo.meetingscheduler.application.port.outbound.");

	@ArchTest
	static final ArchRule persistenceAdapterOnlyImplementsOutboundPorts = noClasses().that()
			.resideInAPackage("com.demo.meetingscheduler.adapter.outbound..").should().dependOnClassesThat()
			.resideInAnyPackage("com.demo.meetingscheduler.application.service..", "com.demo.meetingscheduler.application.port.in..");

}
