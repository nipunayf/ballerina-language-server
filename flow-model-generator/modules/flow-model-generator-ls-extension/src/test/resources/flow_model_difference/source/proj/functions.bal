import ballerina/io;
import ballerina/random;

function getLocationInput() returns string|error {
    io:print("Enter location: ");
    string location = io:readln();

    if location.trim().length() == 0 {
        return error("Location cannot be empty");
    }

    return location.trim();
}

function getWeatherData(string location) returns WeatherData|error {
    // Mock weather service - in real application, this would call a weather API
    float randFloat = random:createDecimal();
    int randInt = check random:createIntInRange(0, 30);

    string[] conditions = ["Sunny", "Cloudy", "Rainy", "Partly Cloudy", "Thunderstorm", "Foggy"];
    string[] directions = ["North", "South", "East", "West", "Northeast", "Northwest", "Southeast", "Southwest"];

    WeatherData weather = {
        location: location,
        temperature: 15.0 + (randFloat * 25.0),
        condition: conditions[randInt],
        humidity: 30 + randInt,
        windSpeed: randFloat * 30.0,
        windDirection: directions[randInt]
    };

    return weather;
}

function getWeatherAdvice(WeatherData weather) returns string {
    if weather.condition == "Rainy" || weather.condition == "Thunderstorm" {
        return "Advice: Don't forget your umbrella!";
    } else if weather.temperature > 30.0 {
        return "Advice: It's hot outside, stay hydrated!";
    } else if weather.temperature < 10.0 {
        return "Advice: Bundle up, it's quite cold!";
    } else if weather.condition == "Sunny" {
        return "Advice: Perfect weather for outdoor activities!";
    } else {
        return "Advice: Have a great day!";
    }
}
