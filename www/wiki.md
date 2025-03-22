# WebSocket Protocol
### Status responses
```json5
{
    "cmd": "response",
    "targetSystem": "string",
    "content": {
        "code": 0,                  // Error code, 2 = OK; 3 = Warn; 4 = Error
        "message": "string",        // Error Message
        "sourceCommand": "string"   // Command that caused the error
    }
}
```

### General packet structure
```json5
{
    "cmd": "string",           // Command
    "targetSystem": "string",  // Categorization of the system that should handle the command
    "content": {}              // Actual data
}
```

---

# TargetSystem: Default

## Command: "ping"
```json5
{
    "cmd": "ping",         
    "targetSystem": "default",
    "content": {
      "id": "string"        // Custom string that gets returned in the response
    }
}
```

### Response
```json5
{
  "cmd": "pong",
  "targetSystem": "default",
  "content": {
    "id": "string"        // Data from the ping packet
  }
}
```

## Command: "syn"
This command requests all relevant data from the server
```json5
{
    "cmd": "syn",         
    "targetSystem": "default",
    "content": {} // Empty content
}
```

---

### Response
Data for the downloads page
```json5
{
    "cmd": "syn",         
    "targetSystem": "default",
    "content": {
        "data": [
            {
                "uuid": "string",     // Unique identifier for the episode
                "state": "string",    // Download status of the object
                "target": "string",   // Target folder (uses the targets from the config ex: "stream-series")
                "url": "string",      // Download URL
                "sortIndex": 0,       // Sort index of the item
                "options": {
                    "enableSeasonAndEpisodeRenaming": "true|false",
                    "enableSessionRecovery": "true|false",
                    "useDirectMemory": "true|false"
                },
                "autoloaderData": { // Autoloader data is optional, and only available when the autoloader has added the task
                    "animeId": 0,
                    "seasonId": 0,
                    "episodeId": 0,
                    "provider": "string" // Optional: Alternative provider from wich the anime should be downloaded
                }
            },
            ....
        ]
    }             
}
```

Data for settings
```json5
{
    "cmd": "setting",         
    "targetSystem": "default",
    "content": {
      "settings": [
        {
          "key": "string",   // Setting key
          "value": "string"  // Setting value
        },
        ...
      ]
    }             
}
```
Target Data
```json5
{
    "cmd": "targetFolders",         
    "targetSystem": "default",
    "content": {
        "targets": [
          {
            "identifier": "string",
            "displayName": "string"
          },
          ...
        ]
    }             
}
```

---

## Command: "put"
```json5
{
    "cmd": "put",         
    "targetSystem": "default",
    "content": {
      "list": [
        {
          "uuid": "string",     // Unique identifier for the episode
          "state": "string",    // Download status of the object
          "target": "string",   // Target folder (uses the targets from the config ex: "stream-series")
          "url": "string",      // Download URL
          "subdirectory": "string", // Ootional: Subdirectory of the target folder
          "aniworldUrl": "string",  // Optional: Aniworld url
          "options": {
            "enableSeasonAndEpisodeRenaming": "true|false",
            "enableSessionRecovery": "true|false",
            "useDirectMemory": "true|false"
          }
        },
        ...
      ]
    }
}
```
### Responses
4: "Invalid URL" -> [via Status response](#status-responses)

---

## Command: "del"
```json5
{
    "cmd": "del",
    "targetSystem": "default",
    "content": {
        "uuid": "string"    // UUID of the item to be deleted
    }
}
```
### Responses
4: "Cannot remove task, already downloading!" -> [via Status response](#status-responses)

**BROADCAST**:
```json5
{
    "cmd": "del",
    "targetSystem": "default",
    "content": {
        "list": [
          "string"     // UUID of the item to be deleted
        ]
    }
}
```

---

## Command: "del-all"
```json5
{
    "cmd": "del-all",
    "targetSystem": "default",
    "content": {} // Empty content
}
```
### Responses
**BROADCAST**:
```json5
{
    "cmd": "del",
    "targetSystem": "default",
    "content": {
        "list": [
          "string",    // UUID of the item to be deleted
          "string",    // UUID of the item to be deleted
          "string"     // UUID of the item to be deleted
        ]
    }
}
```

---

## Command: "setting"
```json5
{
    "cmd": "setting",
    "targetSystem": "default",
    "content": {
        "key": "string",    // Setting key/identifier
        "val": "string"     // Setting value
    }
}
```
### Responses
4: "Unsupported setting" -> [via Status response](#status-responses)

**Broadcast**
```json5
{
    "cmd": "setting",
    "targetSystem": "default",
    "content": {
    "settings": [
      {
        "key": "string",   // Setting key
        "value": "string"  // Setting value
      },
      ...
    ]
  }
}
```

---

## Command: "requestSubfolders"
```json5
{
  "cmd": "requestSubfolders",
  "targetSystem": "default",
  "content": {
    "selection": "string"   // Target ex: "stream-series/"
  }
}
```
### Responses
4: "No sub-folders are configured for {text}" -> [via Status response](#status-responses)

```json5
{
  "cmd": "requestSubfoldersResponse",
  "targetSystem": "default",
  "content": {
    "subfolders": [
      "string",
      "string",
      ...
    ]
  }
}
```

## Command: "systemInfo"
```json5
{
    "cmd": "systemInfo",
    "targetSystem": "default",
    "content": {} // Empty content
}
```
### Responses

```json5
{
  "cmd": "systemInfo",
  "targetSystem": "default",
  "content": {
    "memory": {
      "current": 0,
      "heap": 0,
      "max": 0
    },
    "docker": {
      "memoryLimit": 0,
      "memoryUsage": 0
    },
    "threadPool": {
      "active": 0,
      "core": 0,
      "max": 0,
      "pool": 0,
      "completed": 0
    },
    "aniworld": {
      "<key>": 0,
      "<key>": 0,
      "<key>": 0,
      ...
    }
  }
}
```

---

# TargetSystem: Aniworld

## Command: "resolve"
```json5
{
    "cmd": "resolve",
    "targetSystem": "aniworld",
    "content": {
        "url": "string",    // Aniworld anime or season url
        "language": 0       // 1 = German; 2 = German Sub; 3 = English Sub
    }
}
```
### Responses
4: "Exception" -> [via Status response](#status-responses)

---

# TargetSystem: Autoloader
## Command: "getData"
```json5
{
    "cmd": "getData",
    "targetSystem": "autoloader",
    "content": {} // Empty content
}
```
### Responses
```json5
{
    "cmd": "syn",
    "targetSystem": "autoloader",
    "content": {
        "items": [
            {
                "id": 0,
                "languageId": 0,              // 1 = German; 2 = German Sub; 3 = English Sub
                "title": "string",            // Title of the anime
                "url": "string",              // Aniworld url
                "unloaded": 0,                // Number of episodes missing / not downloaded
                "lastScan": 0,                // Last active scan for new episodes
                "directory": "string",        // Download directory, relative to target folder
                "excludedSeasons": "0,1,2,3"  // Comma separated list of seasons to exclude
            },
            ...
        ]
    }
}
```

---

## Command: "subscribe"
```json5
{
    "cmd": "subscribe",
    "targetSystem": "autoloader",
    "content": {
        "url": "string",              // Aniworld anime url
        "languageId": 0,              // 1 = German; 2 = German Sub; 3 = English Sub
        "directory": "string",        // Local directory
        "excludedSeasons": "0,1,2,3"  // Optional: Comma separated list of seasons to exclude
    }
}
```
### Responses
4: "Failed to subscribe to {url} this url is already subscribed!" -> [via Status response](#status-responses)
4: "Failed to subscribe to {url} cannot parse title!" -> [via Status response](#status-responses)
2: "OK" -> [via Status response](#status-responses)
---

## Command: "unsubscribe"
```json5
{
    "cmd": "unsubscribe",
    "targetSystem": "autoloader",
    "content": {
        "id": 0     // Identifier of the anime
    }
}
```
### Responses
4: "Tried to remove anime with id {id} but this does not exist." -> [via Status response](#status-responses)
2: "OK"
```json5
{
    "cmd": "del",
    "targetSystem": "autoloader",
    "content": {
      "id": 0      // Identifier of the anime
    }
}
```

---

## Command: "runDownload"
```json5
{
  "cmd": "runDownload",
  "targetSystem": "autoloader",
  "content": {
    "id": 0     // Identifier of the anime
  }
}
```
### Responses
"Anime with id {id} not found!" -> [via Status response](#status-responses)
2: "OK" -> [via Status response](#status-responses)

---

## Command: "getAlternateStreams"
Gets alternate stream providers for an autoloader added episode
```json5
{
    "cmd": "getAlternateProviders",
    "targetSystem": "autoloader",
    "content": {
      // Identifiers can be obtained via the objects from the syn command in the default target system
      "animeId": 0,     // Identifier of the anime
      "seasonId": 0,    // Identifier of the season
      "episodeId": 0    // Identifier of the episode
    }
}
```
### Responses
```json5
{
    "cmd": "getAlternateProvidersResponse",
    "targetSystem": "autoloader",
    "content": {
      "providers": [
        "VOE",
        "Doodstream",
        "Vidoza",
        "Streamtape"
      ]
    }
}
```