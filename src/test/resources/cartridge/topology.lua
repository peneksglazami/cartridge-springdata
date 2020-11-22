cartridge = require('cartridge')
replicasets = {{
    alias = 'app-router',
    roles = {'app.roles.api_router'},
    join_servers = {{uri = 'localhost:3301'}}
}, {
    alias = 's1-storage',
    roles = {'app.roles.api_storage'},
    join_servers = {{uri = 'localhost:3302'}, {uri = 'localhost:3303'}}
}, {
    alias = 's2-storage',
    roles = {'app.roles.api_storage'},
    join_servers = {{uri = 'localhost:3304'}, {uri = 'localhost:3305'}}
}}
return cartridge.admin_edit_topology({replicasets = replicasets})
