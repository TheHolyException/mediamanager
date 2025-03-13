# WebSocket Protocol
### Status responses
```json
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
```json
{
    "cmd": "string",           // Command
    "targetSystem": "string",  // Categorization of the system that should handle the command
    "content": {}              // Actual data
}
```

---
---

# TargetSystem: Default

## Command: "syn"
This command requests all relevant data from the server
```json
{
    "cmd": "syn",         
    "targetSystem": "default",
    "content": {} // Empty content
}
```

---

### Response
Data for the downloads page
```json
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
                "options": {
                    "enableSeasonAndEpisodeRenaming": "true|false",
                    "enableSessionRecovery": "true|false",
                    "useDirectMemory": "true|false"
                }
            },
            ....
        ]
    }             
}
```
Data for the subscriptions
```json
{
    "cmd": "syn",         
    "targetSystem": "autoloader",
    "content": [
        {
            "id": 0,
            "directory": "string",  // Download directory, relative to target folder
            "languageId": 0,        // 1 = German; 2 = German Sub; 3 = English Sub
            "title": "string",      // Title of the anime
            "unloaded": 0,          // Number of episodes missing / not downloaded
            "url": "string",        // Aniworld url
            "lastScan": 0           // Last active scan for new episodes
        },
        ...
    ]          
}
```
Data for settings
```json
{
    "cmd": "setting",         
    "targetSystem": "default",
    "content": {
        "key": "string",    // Setting key/identifier
        "value": "string"   // Setting value
    }             
}
```
Target Data
```json
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
```json
{
    "cmd": "put",         
    "targetSystem": "default",
    "content": {
        "uuid": "string",     // Unique identifier for the episode
        "state": "string",    // Download status of the object
        "target": "string",   // Target folder (uses the targets from the config ex: "stream-series")
        "url": "string",      // Download URL
        "options": {
            "enableSeasonAndEpisodeRenaming": "true|false",
            "enableSessionRecovery": "true|false",
            "useDirectMemory": "true|false"
        }
    }
}
```
### Responses
4: "Invalid URL" -> [via Status response](#status-responses)

---

## Command: "del"
```json
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
```json
{
    "cmd": "del",
    "targetSystem": "default",
    "content": {
        "uuid": "string"    // UUID of the item to be deleted
    }
}
```

---

## Command: "del-all"
```json
{
    "cmd": "del-all",
    "targetSystem": "default",
    "content": {} // Empty content
}
```
### Responses
**BROADCAST**:
```json
{
    "cmd": "del",
    "targetSystem": "default",
    "content": {
        "uuid": "string"    // UUID of the item to be deleted
    }
}
```

---

## Command: "setting"
```json
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
```json
{
    "cmd": "setting",         
    "targetSystem": "default",
    "content": {
        "key": "string",    // Setting key/identifier
        "value": "string"   // Setting value
    }             
}
```

---

## Command: "requestSubfolders"
```json
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

```json
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

---

# TargetSystem: Aniworld

## Command: "resolve"
```json
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
```json
{
    "cmd": "getData",
    "targetSystem": "autoloader",
    "content": {} // Empty content
}
```
### Responses
```json
{
    "cmd": "syn",
    "targetSystem": "autoloader",
    "content": {
        "items": [
            {
                "id": 0,
                "languageId": 0,        // 1 = German; 2 = German Sub; 3 = English Sub
                "title": "string",      // Title of the anime
                "url": "string",        // Aniworld url
                "unloaded": 0,          // Number of episodes missing / not downloaded
                "lastScan": 0,          // Last active scan for new episodes
                "directory": "string"   // Download directory, relative to target folder
            },
            ...
        ]
    }
}
```

---

## Command: "subscribe"
```json
{
    "cmd": "subscribe",
    "targetSystem": "autoloader",
    "content": {
        "url": "string",        // Aniworld anime url
        "languageId": 0,        // 1 = German; 2 = German Sub; 3 = English Sub
        "directory": "string"   // Local directory
    }
}
```
### Responses
4: "Failed to subscribe to {url} this url is already subscribed!" -> [via Status response](#status-responses)
4: "Failed to subscribe to {url} cannot parse title!" -> [via Status response](#status-responses)
2: "OK" -> [via Status response](#status-responses)
---

## Command: "unsubscribe"
```json
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

---

## Command: "runDownload"
```json
{
    "cmd": "runDownload",
    "targetSystem": "default",
    "content": {
        "id": 0     // Identifier of the anime
    }
}
```
### Responses
"Anime with id {id} not found!" -> [via Status response](#status-responses)
2: "OK" -> [via Status response](#status-responses)