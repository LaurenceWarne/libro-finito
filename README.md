# Fin
[![codecov](https://codecov.io/gh/LaurenceWarne/libro-finito/branch/master/graph/badge.svg?token=IFT4R8T4F3)](https://codecov.io/gh/LaurenceWarne/libro-finito)

`libro-finito` is a HTTP server which provides a local book management service.  Its main features are searching for books and aggregating books into user defined collections, which are persisted on disk on an sqlite db.  The main entry point is a graphql API located [here](/schema.gql).  Currently the only client application is [finito.el](https://github.com/LaurenceWarne/finito.el) (for Emacs).

Also check out the [Changelog](/CHANGELOG.md).

# Configuration

The server may be configured in a number of ways via a [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) file whose expected location is `$XDG_CONFIG_HOME/libro-finito/service.conf`:

```hocon
port = 56848,
default-collection = "My Books",
special-collections = [
  {
    name = "My Books",
    lazy = false,
    add-hook = "add = true"
  },
  {
    name = "Currently Reading",
    read-started-hook = "add = true",
    read-completed-hook = "remove = true"
  },
  {
    name = "Read",
    read-completed-hook = "add = true"
  },
  {
    name = "Favourites",
    rate-hook = """
      if(rating >= 5) then
        add = true
      else
        remove = true
      end
    """
  }
]
```

`default-collection` is the collection which books will be added to in the case no collection is specified in the `addBook` mutation.

The sqlite database is located in `$XDG_DATA_HOME/libro-finito/db.sqlite`.

## Special Collections

`libro-finito` allows us to mark some collections as **special**, these collections allow for books to be added or removed automatically via **hooks** whose behaviours are described in [lua](https://www.lua.org/).

For example, the `My Books` special collection defines one hook, the `add-hook`, which simply sets the variable `add` to `true`.  The `add-hook` is called whenever a book is added to a collection.  It receives the book attributes as variable bindings and books will be added or removed from the collection according to the values of the `add` and `remove` variables set by the hook (setting neither of these is a nop).

Therefore the `add-hook` above on the `My Books` special collection will simply add any book added to any other collection to the `My Books` collection.  Available hooks are:

* `add-hook` called when a book is added to a collection
* `remove-hook` called when a book is removed from a collection
* `rate-hook` called when a book is rated
* `read-begun-hook` called when a book has been started (ie marked as "in progress")
* `read-completed-hook` called when a book has been finished

In the configuration above some special collections have been marked as not `lazy`, which means the service will create them on startup if it detects they do not exist as opposed to the default which is creating them as soon as a book is added to them via a hook (they can also be manually created).

The special collections enabled by default are those defined in the above snippet - so `My Books`, `Currently Reading`, `Read` and `Favourites`.

# Local Development

Optionally install [mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html#_installation) (otherwise swap `mill` for `./mill` below).  You can start the server via:

```bash
mill finito.main.run
```

You can then open the playground at http://localhost:56848/graphiql, alternatively you can curl:

```bash
curl 'http://localhost:56848/api/graphql' -H 'Accept-Encoding: gzip, deflate, br' -H 'Content-Type: application/json' -H 'Accept: application/json' --data-binary '{"query":"query {\n  collection(name: \"My Books\") {\n    name\n    books {\n      title\n    }\n  }\n}"}' --compressed
```

Setting `LOG_LEVEL` to `DEBUG` will prompt more verbose output.

All tests can be run using:

```bash
mill __.test
```
