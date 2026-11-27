/// URL de la API Spring. Se define al compilar, sin dejarla fija en el codigo:
///   flutter run --dart-define=API_URL=http://192.168.1.50:8081
/// Por defecto apunta a la PC desde el emulador de Android (10.0.2.2 = localhost de la PC).
const String apiUrl = String.fromEnvironment('API_URL', defaultValue: 'http://10.0.2.2:8081');
