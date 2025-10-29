export interface ClientListItem {
    ip: string;
    active: boolean;
    status: string;
}

interface ClientListProps {
    items: ClientListItem[];
    heading: string;
    onSelectItem: (item: ClientListItem) => void;
}

function ClientList({items, heading, onSelectItem}: ClientListProps) {
    // Sort items alphabetically by Active status and then by IP address
    const sortedItems = [...items].sort((a, b) => {
        if (a.active === b.active) {
            return a.ip.localeCompare(b.ip);
        }
        return a.active ? -1 : 1;
    });

    if (sortedItems.length === 0)
        return <><h1>List</h1><p>No Clients Found</p></>;

    return (
        <>
            <h1>{heading}</h1>
            <ul className="list-group">
                {sortedItems.map((item) =>
                    <li
                        className="list-group-item"
                        key={item.ip}
                        onDoubleClick={() => {
                            onSelectItem(item)
                        }}
                        style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}
                    >
                        <span>{item.ip}</span>
                        <span
                            style={{
                                color: item.active ? "green" : "red",
                                fontWeight: "bold",
                                marginLeft: "1rem"
                            }}
                        >
                            {item.status}
                        </span>
                    </li>
                )}
            </ul>
        </>
    );
}

export default ClientList;