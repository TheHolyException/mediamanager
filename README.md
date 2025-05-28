# MediaManager

A Java-based media management system with a web interface for organizing and automating media downloads, particularly focused on TV shows and anime.

## Features

- **Web-based Interface**: Modern, responsive dashboard for managing media
- **Download Management**: Queue and monitor downloads with progress tracking
- **Auto-loader**: Automatically download new episodes from subscribed series
- **Multi-source Support**: Supports multiple streaming sources and providers
- **Customizable Dashboard**: Widget-based interface with drag-and-drop functionality
- **Statistics**: Track download history and media library statistics
- **Settings Management**: Configure application behavior through the web interface

## Requirements

- Java 11 or higher
- Maven 3.6.0 or higher
- MySQL 8.0.33 or higher or MariaDB 10.11.2 or higher
- Web browser with JavaScript support

## Installation

1. Clone the repository:
   ```bash
   git clone [repository-url]
   cd MediaManager
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. Run the application:
   ```bash
   java -jar target/MediaManager-1.0.jar
   ```

4. Access the web interface at `http://localhost:8080`

## Configuration

Create a `config` directory and add the following configuration files:

1. `config.toml` - Main application configuration
2. `system-settings.json` - System settings and preferences

## Docker Support

The application can be run in a Docker container. Use the provided `Dockerfile` to build the container:

```bash
docker build -t mediamanager .
docker run -p 8080:8080 -v $(pwd)/data:/app/data mediamanager
```

## Project Structure

- `src/main/java` - Java source code
  - `de.theholyexception.mediamanager` - Main application package
    - `handler` - Request handlers for different functionalities
    - `models` - Data models
    - `settings` - Configuration and settings management
    - `util` - Utility classes
    - `webserver` - Web server and WebSocket implementation
- `www` - Web interface files
  - `frameworks` - Third-party libraries and frameworks
  - `scripts` - JavaScript files
  - `style` - CSS stylesheets

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a new Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, please open an issue in the GitHub repository.
