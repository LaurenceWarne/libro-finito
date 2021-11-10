# Fin
[![codecov](https://codecov.io/gh/LaurenceWarne/libro-finito/branch/master/graph/badge.svg?token=IFT4R8T4F3)](https://codecov.io/gh/LaurenceWarne/libro-finito)

`libro-finito` is a http service whose goal is to provide a local book management service.

Its main features are searching for books and aggregating books into user defined collections.

It's sole entry point is a graphql API located [here](/schema.gql).

Also check out the [Changelog](/CHANGELOG.md).

# Configuration

The server may be configured in a number of ways by via a file whose expected location is `~/.config/libro-finito/service.conf`:

```hocon
{
  database-path = ~/.config/libro-finito/db.sqlite,
  port = 56848,
  default-collection = My Books,
  special-collections = [
    {
      name = My Books,
      lazy = false,
      add-hook = "add = true"
    },
    {
      name = Currently Reading,
      read-begun-hook = "add = true",
      read-complete-hook = "remove = true"
    },
    {
      name = Read,
      read-completed-hook = "add = true"
    },
    {
      name = Favourites,
      rate-hook = """
        if(rating >= 5) then
          add = true
        else
          remove = true
        end
      """
    }
  ]
}
```

`database-path` is the path of the sqlite database the service will use whilst `port` is the port which the http server will use.

`default-collection` is the collection which books will be added to in the case no collection is specified in the `addBook` mutation.

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
