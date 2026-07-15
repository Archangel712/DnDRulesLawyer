# Teststrategie Meilenstein 4

## Ziel

Die Tests pruefen die zentralen Komponenten der App ueber ihre oeffentlichen Schnittstellen. Schnelle lokale Unit-Tests bilden die Basis. Android-spezifische Tests werden gezielt fuer Room-DAOs und zentrale UI-Flows eingesetzt.

## Testpyramide

| Ebene | Zweck | Umsetzung |
|---|---|---|
| Unit-Tests | Schnelle Pruefung von Domain-Logik, Mappern, Repository-Verhalten, Custom-Resource-Erstellung und HTML-Rendering | `app/src/test` |
| Instrumentation-Tests | Pruefung Android-spezifischer Infrastruktur wie Room | `app/src/androidTest/.../RuleDaoInstrumentedTest.kt` |
| Espresso-Tests | Pruefung zentraler Nutzerfluesse im finalen Produkt | `app/src/androidTest/.../MainActivitySearchFlowTest.kt` |
| Manuelle Tests | Pruefung schwer automatisierbarer Geraetefunktionen und visueller Details | Checkliste fuer Spracheingabe, WebView-Darstellung, Layout und reale API-Verbindung |

## Komponentenabdeckung

| Komponente / Interface | Automatisierte Tests | Testziel |
|---|---|---|
| `RulesRepository` | `DefaultRulesRepositorySearchTest`, `DefaultRulesRepositoryCustomRulesTest`, `DefaultRulesRepositorySyncAndDetailsTest` | Suche, Filter, Favoriten, Custom Rules, Sync und Detail-Caching |
| `DndApiService` | Fake-API in Repository-Tests | Repository bleibt von echter Netzwerkverbindung entkoppelt |
| `RuleResourceDao`, `RuleDetailDao` | `RuleDaoInstrumentedTest` | Room-Suche, Favoriten, Upsert, Delete und Detail-Beziehungen |
| Mapper | `LocalRuleMapperTest`, `LocalRuleDetailMapperTest`, `RemoteRuleMapperTest`, `RemoteRuleDetailMapperTest` | Korrekte Umwandlung zwischen API-/DB-Modellen und Domain-Modell |
| Custom-Resource-Erstellung | `CustomResourceDetailFactoryTest` | Strukturierte Domain- und JSON-Daten fuer eigene Ressourcen |
| Suchfilter | `RuleSearchFiltersTest` | Kombination von Typ-, Quellen- und Favoritenfiltern |
| Detail-Rendering | `RuleDetailHtmlRendererTest` | Robuste HTML-Ausgabe fuer zentrale Ressourcentypen |
| UI / Espresso | `MainActivitySearchFlowTest` | Suche aus UI starten, Ergebnis anzeigen und Detailansicht oeffnen |

## Dependency-Strategie

Die Komponenten werden moeglichst ueber Interfaces getestet. Das Repository nutzt in Unit-Tests Fake-DAOs und eine Fake-API. Die UI kann fuer Espresso-Tests ein Fake-Repository erhalten, damit UI-Flows ohne echte Netzwerk- oder Datenbankabhaengigkeit stabil laufen.

## Ausfuehrung

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` benoetigt einen laufenden Emulator oder ein angeschlossenes Android-Geraet.
