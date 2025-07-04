
public function main() returns error? {
    string location = check getLocationInput();
    WeatherData weather = check getWeatherData(location);
}
