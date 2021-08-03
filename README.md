# Automotive-apk
## Student project
### [Complementary project](https://github.com/AKrupaa/automotive-stm32)

### Preview
![](/images/demo.png)   ![](/images/car-demo.png) <br>
[Youtube sample 1](https://youtu.be/acJ9b4zpFhg)
[Youtube sample 2](https://youtu.be/u2U2tENVD00)

## Usage
Allows to communicate via BLE with STM32 device to control the movement and logging most of the data to file

## Description
#### EN below
#### PL:
Założenia projektowe:
1. Pojazd musi się poruszać autonomicznie, może być kontrolowany zdalnie  bez wglądu na trasę (kontrola w oparciu o dane z sensorów) lub z wglądem na trasę.
2. Pojazd musi być zabezpieczony przed przypadkowym, bądź celowym wprowadzeniem na przeszkodę. 
3. Pojazd będzie poruszał się po powierzchni płaskiej około 12 x 20 płytek (płytka 33 x 33 cm). Powierzchnia będzie ograniczona taśmą aluminiową naklejoną na powierzchnię.
4. Całkowite opuszczenie ograniczonego terenu skutkuje powrotem na START i ponownym rozpoczęciem przejazdu.
5. W trakcie prezentacji, pojazd będzie miał za zadanie przejechać od STARTU do METY omijając minimum 3 przeszkody w postaci kartonowych ścian (grubość ścian około 10 cm , wysokość około 20 cm, ściany w kolorach jasnych) w myśl zasady „Im szybciej tym lepiej”. Czas przejazdu nie może być jednak dłuższy niż 5 min (podana wartość jest orientacyjna).
6. Lokalizacje STARTU oraz METY będą ustalone i oznaczone znacznikiem  w postaci magnesu. Informacja o bokach, na których będą znajdowały się START i META zostaną podane na 10 dni przed pokazem.
7. System ma za zadanie zbierać dane dot. czasu przejazdu + minimum jeden dodatkowy parametr.
8. W najprostszym rozwiązaniu zebrane dane muszą być przesłane po przejechaniu „zadanej” trasy na telefon komórkowy i dołączone do raportu końcowego.
9. Platforma mikroprocesorowa STM32.
10. Pojazd nie może mieć wystających części poza obrys podwozia.

#### EN:
Design assumptions:
1. The vehicle must be autonomous, it can be controlled remotely without the view of the route (control based on data from sensors) or with the view of the route.
2. The vehicle must be secured against accidentally or intentionally entering an obstacle.
3. The vehicle will run on a flat surface of approximately 12 x 20 tiles (33 x 33 cm tile). The area will be limited by aluminum tape stuck to the surface.
4. The complete leaving of the restricted area results in the return to START and the re-start of the journey.
5. During the presentation, the vehicle will have to drive from START to FINISH, avoiding at least 3 obstacles in the form of cardboard walls (wall thickness about 10 cm, height about 20 cm, light-colored walls) in accordance with the principle "The sooner the better" . The travel time, however, cannot be longer than 5 minutes (the given value is approximate).
6. The START and FINISH locations will be fixed and marked with a marker in the form of a magnet. Information about the sides on which the START and FINISH will be located will be announced 10 days before the show.
7. The system's task is to collect data on travel time + at least one additional parameter.
8. In the simplest solution, the collected data must be sent to the mobile phone after completing the "set" route and attached to the final report.
9. STM32 microprocessor platform.
10. The vehicle must not have any protruding parts beyond the contour of the chassis.

## What did I use?

[RxAndroidBle](https://github.com/Polidea/RxAndroidBle) - for establishing BLE communication
[Developer Guides | Android](https://developer.android.com/guide/) - for building the app
